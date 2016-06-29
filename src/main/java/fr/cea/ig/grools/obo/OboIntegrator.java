package fr.cea.ig.grools.obo;
/*
 * Copyright LABGeM 13/03/15
 *
 * author: Jonathan MERCIER
 *
 * This software is a computer program whose purpose is to annotate a complete genome.
 *
 * This software is governed by the CeCILL  license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */


import ch.qos.logback.classic.Logger;
import fr.cea.ig.grools.Integrator;
import fr.cea.ig.grools.Reasoner;
import fr.cea.ig.grools.fact.*;
import fr.cea.ig.grools.fact.Relation;
import fr.cea.ig.io.model.obo.*;
import fr.cea.ig.io.parser.OboParser;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
/*
 * @startuml
 * class OboIntegrator{
 *  -kieSession :  KieSession
 * }
 * @enduml
 */
public class OboIntegrator implements Integrator {
    private static final int PAGE_SIZE              = 4_096;
    private static final int DEFAULT_NUMBER_PAGE    = 10;
    private static final String     SOURCE = "Unipathway OBO 09/06/15";
    private static final Logger     LOG     = (Logger) LoggerFactory.getLogger(OboIntegrator.class);
    private        final Reasoner   grools;
    private        final String     source;
    private        final Map<String, Set<UER>> metacycToUER;

    @NonNull
    private final InputStream obo;


    @NonNull
    @Getter
    private final OboParser oboParser;

    @NonNull
    private static InputStream getFile(@NonNull final String fileName) {
        ClassLoader classLoader = OboIntegrator.class.getClassLoader();
        return classLoader.getResourceAsStream(fileName);
    }

    private static long countOccurences(@NonNull String s, char c){
        return s.chars().filter(ch -> ch == c).count();
    }

    @NonNull
    private static String processId( @NonNull final String id ){
        return id.replace("UPa:", "");
    }


    @NonNull
    public static Map<String,Set<UER>> metacycToUER(@NonNull final InputStream metacycMappingFileName, @NonNull final OboParser oboParser){

        final Map<String,Set<UER>> mapping         = new HashMap<>();
        BufferedReader              br              = null;
        InputStreamReader           isr             = null;
        String                      line            = "";
        String[]                    currentValues   = null;
        try {
            isr         = new InputStreamReader( metacycMappingFileName, Charset.forName("US-ASCII") );
            br          = new BufferedReader(isr, PAGE_SIZE * DEFAULT_NUMBER_PAGE );
            line        = br.readLine();
            while( line != null ){
                if(  line.charAt(0) != '*'){
                    currentValues = line.split("\t");
                    assert currentValues.length == 4;
                    Set<UER> values = mapping.get(currentValues[3]);
                    if( values == null) {
                        values = new HashSet<>();
                        mapping.put(currentValues[3], values);
                    }
                    values.add((UER) oboParser.getTerm("UPa:"+currentValues[2]));
                }
                line        = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if( br != null ) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return  mapping;
    }

    private PriorKnowledge getPriorKnowledge(@NotNull final Term term){
        PriorKnowledge pk = grools.getPriorKnowledge(term.getId());
        if( pk == null ){
            pk = PriorKnowledgeImpl.builder()
                    .name(term.getId())
                    .label(term.getName())
                    .source(source)
                    .description(term.getDefinition())
                    .build();
            grools.insert(pk);
        }
        return pk;
    }

    public OboIntegrator( @NonNull final Reasoner reasoner ) throws Exception {
        obo         = getFile( "unipathway.obo" );
        oboParser   =  new OboParser( obo );
        grools      = reasoner;
        source      = SOURCE;
        metacycToUER= metacycToUER( getFile("unipathway2metacyc.tsv"),oboParser );
    }

    public OboIntegrator( @NonNull final Reasoner reasoner,  @NonNull final File oboFile, @NotNull final String source_description ) throws Exception {
        obo         = new FileInputStream( oboFile );
        oboParser   = new OboParser( obo );
        grools      = reasoner;
        source      = source_description;
        metacycToUER= metacycToUER( getFile("unipathway2metacyc.tsv"),oboParser );
    }


    @Override
    public void integration() {
        final Iterator<Map.Entry<String, Term>> it = oboParser.iterator();
        while( it.hasNext() ){
            final Map.Entry<String, Term>   entry   = it.next();
            final Term                      term    = entry.getValue();
            final PriorKnowledge            parent  = getPriorKnowledge( term );

            if( term instanceof TermRelations ) {
                final TermRelations tr = (TermRelations) term;

                if( tr instanceof UPA){
                    final UPA upa = (UPA) tr;
                    for(final fr.cea.ig.io.model.obo.Relation isA : upa.getIsA() ){
                        final Term              termType    = oboParser.getTerm(isA.getIdLeft());
                        final PriorKnowledge    pkType      = getPriorKnowledge(termType);
                        final Relation          relType     = new RelationImpl(parent, pkType, RelationType.SUBTYPE);
                        grools.insert(relType);
                    }
                    if( upa.getSuperPathway() != null ){
                        final Term              superPath   = oboParser.getTerm( upa.getSuperPathway().getIdLeft() );
                        final PriorKnowledge    pkSuperPath = getPriorKnowledge(superPath);
//                        final Relation          relSuperPath= new RelationImpl(parent, pkSuperPath, RelationType.PART);
                        final Relation          relSuperPath= new RelationImpl(parent, pkSuperPath, RelationType.SUBTYPE); // should be part but unipathway use super pathway definition inconsistently
                        grools.insert(relSuperPath);
                    }
                }

                final List<Variant> variants = new ArrayList<>();
                Variant.getVariant(tr.getChildren(), variants);
                if( variants.size() > 1) {
                    int i = 1;
                    for (final Variant variant : variants) {
                        final String            name= "variant-" + String.valueOf(i)+'-'+term.getId();
                        final PriorKnowledge    v   = PriorKnowledgeImpl.builder()
                                                                        .name(name)
                                                                        .label(term.getName())
                                                                        .source(source)
                                                                        .build();
                        final Relation rel = new RelationImpl(v, parent, RelationType.SUBTYPE);
                        grools.insert(v, rel);
                        for (final Term child : variant) {
                            final PriorKnowledge pkChild = getPriorKnowledge(child);
                            final Relation relChild = new RelationImpl(pkChild, v, RelationType.PART);
                            grools.insert(relChild);
                        }
                        i++;
                    }
                }
                else{
                    for (final Variant variant : variants) {
                        for (final Term child : variant) {
                            final PriorKnowledge pkChild = getPriorKnowledge(child);
                            final Relation relChild = new RelationImpl(pkChild, parent, RelationType.PART);
                            grools.insert(relChild);
                        }
                    }
                }
            }
        }
    }

    @Override
    public Set<PriorKnowledge> getPriorKnowledgeRelatedToObservationNamed(@NonNull final String source, @NonNull final String id) {
        Set<PriorKnowledge> results;
        results = oboParser.stream().filter( entry -> entry.getValue().getXref(source) != null )
                                    .filter( entry -> entry.getValue().getXref(source).stream()
                                                                                      .anyMatch( ref -> {
                                                                                                            boolean hasMatch = false;
                                                                                                            if(source.equals("EC"))
                                                                                                                hasMatch = (ref.getId().equals(id) || ref.getId().startsWith(id + '.'));
                                                                                                            else
                                                                                                                hasMatch = ref.getId().equals(id);
                                                                                                            return hasMatch;
                                                                                                        }))
                                    .map( entry -> getPriorKnowledge(entry.getValue()))
                                    .collect(Collectors.toSet());
        if( source.equals("METACYC") && results.isEmpty() && metacycToUER.containsKey(id)){
            for( final UER uer : metacycToUER.get(id) ){
                PriorKnowledge pk = getPriorKnowledge(uer);
                results.add(pk);
            }
//            results.addAll( metacycToUER.get(id)
//                                        .stream()
//                                        .map(this::getPriorKnowledge)
//                                        .collect(Collectors.toSet()) );
        }
        return results;
    }
}
