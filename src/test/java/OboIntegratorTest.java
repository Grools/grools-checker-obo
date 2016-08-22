/*
 * Copyright LABGeM 26/03/15
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


import fr.cea.ig.bio.model.obo.UER;
import fr.cea.ig.bio.model.obo.UPA;
import fr.cea.ig.grools.reasoner.Reasoner;
import fr.cea.ig.grools.reasoner.ReasonerImpl;
import fr.cea.ig.grools.fact.PriorKnowledge;
import fr.cea.ig.grools.fact.Relation;
import fr.cea.ig.grools.fact.RelationType;
import fr.cea.ig.grools.obo.OboIntegrator;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.*;


public class OboIntegratorTest {
    
    private Reasoner      grools;
    private OboIntegrator oboIntegrator;
    
    @Before
    public void setUp( ) throws Exception {
        grools = new ReasonerImpl( );
        assertNotNull( grools );
        oboIntegrator = new OboIntegrator( grools );
        assertNotNull( oboIntegrator );
        oboIntegrator.integration( );
    }
    
    
    @Test
    public void testPathwayIntegration( ) {
        final PriorKnowledge isA = grools.getPriorKnowledge( "UPA00404" );
        
        assertNotNull( isA );
        assertTrue( isA.getName( ).equals( "UPA00404" ) );
        
        final PriorKnowledge superPathway = grools.getPriorKnowledge( "UPA00402" );
        
        assertNotNull( superPathway );
        assertTrue( superPathway.getName( ).equals( "UPA00402" ) );
        
        final PriorKnowledge upa33 = grools.getPriorKnowledge( "UPA00033" );
        
        assertNotNull( upa33 );
        assertTrue( upa33.getName( ).equals( "UPA00033" ) );
        
        Relation relIsA = grools.getRelation( upa33, isA, RelationType.SUBTYPE );
        assertNotNull( relIsA );
        
        //Relation relSuperPath = grools.getRelation(upa33, superPathway, RelationType.PART); // wrong usage of superpathway definition from unipathway
        Relation relSuperPath = grools.getRelation( upa33, superPathway, RelationType.SUBTYPE );
        assertNotNull( relSuperPath );
        
        
        final Set<Relation> relPath = grools.getRelationsWithTarget( upa33 );
        assertNotNull( relPath );
        assertEquals( 2, relPath.size( ) );
        
        final PriorKnowledge uls12 = grools.getPriorKnowledge( "ULS00012" );
        
        assertNotNull( uls12 );
        assertTrue( uls12.getName( ).equals( "ULS00012" ) );
        
        Set<Relation> relVariantPath1 = grools.getRelationsWithSource( uls12 );
        assertNotNull( relVariantPath1 );
        assertEquals( 2, relVariantPath1.size( ) );
        for( final Relation relVar : relVariantPath1 ) {
            assertTrue( relPath.stream( ).anyMatch( i -> i.getSource( ) == relVar.getTarget( ) ) );
        }

//         assertTrue( knowledge.getNodeType() == NodeType.OR );
//        final List<PriorKnowledge> variants = grools.getSubKnowledge(knowledge);
//        assertNotNull( variants );
//        final PriorKnowledge UPA00033Variant1 = (variants.get(0).getId().equals("UPA00033-alt-1"))  ? variants.get(0) :  variants.get(1);
//        final PriorKnowledge UPA00033Variant2 = (variants.get(0).getId().equals("UPA00033-alt-2"))  ? variants.get(0) :  variants.get(1);
//        assertTrue( UPA00033Variant1.getId().equals("UPA00033-alt-1") );
//        assertTrue( UPA00033Variant2.getId().equals("UPA00033-alt-2") );
//        final List<PriorKnowledge> uls = grools.getSubKnowledge(UPA00033Variant1);
//        assertNotNull(uls);
//        final PriorKnowledge ULS00013 = (uls.get(0).getId().equals("ULS00013"))  ? uls.get(0) :  uls.get(1);
//        final PriorKnowledge ULS00012 = (uls.get(0).getId().equals("ULS00012"))  ? uls.get(0) :  uls.get(1);
//        assertTrue(ULS00012.getId().equals("ULS00012"));
//        assertTrue(ULS00013.getId().equals("ULS00013"));
    }
    
    @Test
    public void testXref( ) {
        final UPA                 upa33    = ( UPA ) oboIntegrator.getOboReader( ).getTerm( "UPA00033" );
        final UER                 uer28    = ( UER ) oboIntegrator.getOboReader( ).getTerm( "UER00028" );
        final PriorKnowledge      pk33     = grools.getPriorKnowledge( "UPA00033" );
        final PriorKnowledge      pk28     = grools.getPriorKnowledge( "UER00028" );
        final Set<PriorKnowledge> results1 = oboIntegrator.getPriorKnowledgeRelatedToObservationNamed( "KEGG", "map00300" );
        final Set<PriorKnowledge> results2 = oboIntegrator.getPriorKnowledgeRelatedToObservationNamed( "EC", "2.3.3.14" );
        assertNotNull( upa33 );
        assertNotNull( uer28 );
        assertNotNull( results1 );
        assertNotNull( results2 );
        assertTrue( results1.contains( pk33 ) );
        assertTrue( results2.contains( pk28 ) );
    }
    
    
}
