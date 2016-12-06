#!/usr/bin/env python
import re, sys
class Term:
    def __init__(self, t_id, t_name, t_nm, t_def, t_xref, t_relationship):
        self.id = t_id
        self.name = t_name
        self.namespace = t_nm
        self.definition = t_def
        self.xref = t_xref
        self.relationship = t_relationship

    def __str__(self):
        res  = "[Term]\n"
        res +=  "id: "+self.id+"\n"
        res +=  "name: "+self.name+"\n"
        res +=  "namespace: "+self.namespace+"\n"
        res +=  "def: \""+self.definition+"\" [GROOLS]\n"
        for x in self.xref :
            res +=  "xref: "+x+"\n"
        for x in self.relationship :
            res +=  "relationship: "+x+"\n"
        return res

if __name__ == "__main__":
    nm                  = ""
    id                  = ""
    name                = ""
    definition          = ""
    relationship        = set()
    xref                = set()
    duplicates          = {}
    terms               = {}
    def_matcher         = re.compile( r"def: \"(.*)\"")
    id_token            = "id: "
    name_token          = "name: "
    namespace_token     = "namespace: "
    xref_token          = "xref: "
    relationship_token  = "relationship: "
    part_of_token       = "part_of "
    term_is_part_of        =  {}
    
    with open( sys.argv[1], 'r' ) as fout:
        for line in fout:
            if  line.startswith( id_token ) :
                if id != "" :
                    if nm == "enzymatic_reaction":
                        term = Term( id,  name, nm, definition,  set(), set() )
                        term.xref.update( xref )
                        term.relationship.update( relationship )
                        if definition in terms :
                            dup = duplicates.setdefault(definition, set())
                            dup.add( term )
                            first_term = terms[definition]
                            dup.add( first_term )
                        else:
                         terms[definition] = term
                    nm=""
                    definition=""
                    xref=set()
                    relationship=set()
                id=line[len(id_token):].strip()
            elif line.startswith( name_token ):
                name=line[len(name_token):].strip()
            elif line.startswith( namespace_token ):
                nm=line[len(namespace_token):].strip()
            elif def_matcher.match(line):
                definition=def_matcher.match(line).group(1)
            elif line.startswith( xref_token ) :
                xref.add( line[len(xref_token):].strip() )
            elif line.startswith( relationship_token ):
                rel = line[len(relationship_token):].strip()
                relationship.add( rel )
                if rel.startswith( part_of_token ):
                    start =  len(part_of_token)
                    end = rel.find( " ", start)
                    val = rel[start:end]
                    l = term_is_part_of.setdefault( id, [] )
                    l.append( val )

    meta_uer_id = 1

    #has_part_keys = set( term_is_part_of.keys() )
    ref={}
    
    for dup in duplicates:
        dup_def = duplicates[dup]
        new_xref = set()
        new_relationship=set()
        for term in dup_def:
            nm=term.namespace
            definition=term.definition
            ##print term.id,term.definition,term.xref
            new_xref.update( term.xref )
            new_xref.add( term.id )
            new_relationship.update( term.relationship )
            r = ref.setdefault(term, [])
            r.append( "GROOLS:MUER"+str(meta_uer_id) )
        term = Term( "GROOLS:MUER"+str(meta_uer_id), "Meta-UER "+str(meta_uer_id), nm, definition, new_xref, new_relationship )
        print term
        meta_uer_id += 1
   
    for r in ref:
        for i in ref[r]:
            print "sed -i 's/part of " + r.id + "/part of " +  i+"/g'  src/main/resources/unipathway.obo"















