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
import fr.cea.ig.io.model.obo.*;
import fr.cea.ig.io.parser.OboParser;
import fr.cea.ig.grools.Grools;
import fr.cea.ig.grools.biology.BioKnowledgeBuilder;
import fr.cea.ig.grools.biology.BioPriorKnowledge;
import fr.cea.ig.grools.model.FourState;
import fr.cea.ig.grools.model.NodeType;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.io.*;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.*;

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
public class OboIntegrator {
    private static  final int    BUFFER                     = 8192;
    private static  final Logger LOG                        = (Logger) LoggerFactory.getLogger(OboIntegrator.class);
    private         final Grools grools;

    @NotNull
    private InputStream getFile(@NotNull final String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();

        return classLoader.getResourceAsStream(fileName);

    }

    private static long countOccurences(@NotNull String s, char c){
        return s.chars().filter(ch -> ch == c).count();
    }

    @NotNull
    private static String processId( @NotNull final String id ){
        return id.replace("UPa:", "");
    }

    @NotNull
    public static Map<String,String> unipathwayToMetacyc(@NotNull final InputStream metacycMappingFileName){
        final Map<String,String>    mapping         = new HashMap<>();
        BufferedReader              br              = null;
        InputStreamReader           isr             = null;
        String                      line            = "";
        String[]                    currentValues   = null;
        try {
            isr         = new InputStreamReader( metacycMappingFileName, Charset.forName("US-ASCII") );
            br          = new BufferedReader(isr, BUFFER);
            line        = br.readLine();
            while( line != null ){
                if( line.startsWith("UPA")){
                    currentValues = line.split("\t");
                    assert currentValues.length == 4;
                    mapping.put(currentValues[2],currentValues[3]);
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

    private static void pathwayIntegrator( @NotNull final Grools grools, @NotNull final Map<String,String> termToMetacyc, @NotNull final Term term, BioKnowledgeBuilder bioKnowledge, @NotNull final String source ){
        final String                process     = processId(term.getId());
        final List<Variant>         variants    = new ArrayList<>();
        final UnipathwayUnit unit = new UnipathwayUnit(term);
        Variant.getVariant( ((TermRelations)term).getChilds() , variants);
//        LOG.info(term.getClass().toString());
        if( variants.size() == 1 ) {
            if( unit.is(UER.class) ) {
                bioKnowledge = bioKnowledge.setNodeType(NodeType.LEAF);
                final String tId = processId(term.getId());
                final String kId = termToMetacyc.get(tId);
                if( kId != null )
                    bioKnowledge = bioKnowledge.setId(kId);
                else
                    LOG.warn("The process: " + tId + " do not have a corresponding metacyc process!");
            }
            else
                bioKnowledge = bioKnowledge.setNodeType(NodeType.AND);
        }
        else if( variants.size() > 1 )
            bioKnowledge = bioKnowledge.setNodeType(NodeType.OR);

        if( unit.is(UPA.class) ){
            for(final Relation is_a : ((UPA)term).getIsA()){
                final fr.cea.ig.grools.model.Relation r = new fr.cea.ig.grools.model.Relation("is_a", processId(is_a.getIdLeft()), process);
                grools.insert(r);
            }
        }

        final BioPriorKnowledge  bk  = bioKnowledge.create();
//        LOG.info(bk.toString());
        grools.insert(bk);

        if( bk.getNodeType() != NodeType.LEAF ){
            if( variants.size() > 1) {
                for (Integer stepNum = 1; stepNum <= variants.size(); stepNum++) {
                    final String kId = (bk.getNodeType() == NodeType.AND) ? process : process + "-alt-" + stepNum.toString();
                    final BioPriorKnowledge bioKnowledgeVariant = new BioKnowledgeBuilder().setName(term.getName())
                                                                                      .setId(kId)
                                                                                      .setSource(source)
                                                                                      .addPartOf(bk)
                                                                                      .setPresence(FourState.UNKNOWN)
                                                                                      .setNodeType(NodeType.AND)
                                                                                      .setSource(source)
                                                                                      .create();
//                    LOG.info(bioKnowledgeVariant.toString());
                    grools.insert(bioKnowledgeVariant);
                    for (final Term child : variants.get(stepNum - 1)) {
                        final BioKnowledgeBuilder bioknowledgeChild = new BioKnowledgeBuilder().setName(child.getName())
                                                                                               .setId(processId(child.getId()))
                                                                                               .setSource(source)
                                                                                               .addPartOf(bioKnowledgeVariant)
                                                                                               .setPresence(FourState.UNKNOWN);
                        pathwayIntegrator(grools, termToMetacyc, child, bioknowledgeChild, source);
                    }
                }
            }
            else if( variants.size() == 1 ){
                for (final Term child : variants.get(0)) {
                    final BioKnowledgeBuilder bioknowledgeChild = new BioKnowledgeBuilder().setName(child.getName())
                                                                                           .setId(processId(child.getId()))
                                                                                           .setSource(source)
                                                                                           .addPartOf(bk)
                                                                                           .setPresence(FourState.UNKNOWN);

                    pathwayIntegrator(grools, termToMetacyc, child, bioknowledgeChild, source);
                }

            }
        }
    }

    public OboIntegrator(final Grools grools) {
        this.grools = grools;
    }

    public void useDefault() throws IOException, ParseException {
        final InputStream obo = getFile("unipathway.obo");
        final InputStream map = getFile("unipathway2metacyc.tsv");
        use( obo, unipathwayToMetacyc(map) );
    }

    public void use( @NotNull final InputStream oboFileName, @NotNull final Map<String,String> termToMetacyc  ) throws ParseException,IOException {
        use(oboFileName, termToMetacyc, "Unipathway");
    }

    public void use( @NotNull final InputStream oboFileName, @NotNull final Map<String,String> termToMetacyc, @NotNull final String source ) throws ParseException,IOException {
        final OboParser oboParser = new OboParser( oboFileName );
        for( final UPA upa: oboParser.getPathways()){
            final String  process = processId( upa.getId() );
            final BioKnowledgeBuilder bioKnowledge = new BioKnowledgeBuilder().setName(upa.getName())
                                                                              .setId(process)
                                                                              .setPresence(FourState.UNKNOWN)
                                                                              .setSource(source);
            pathwayIntegrator(grools, termToMetacyc, upa, bioKnowledge, source );

        }
    }
}
