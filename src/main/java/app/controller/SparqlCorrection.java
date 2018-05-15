package app.controller;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;

/**
 * Reasons for Sparql Query to not work:
 * 1) Property changes: Returns a new predicate if there is a change in 'prefix' part of the predicate
 * 2) Property not present anymore: (reason could be) 
 * 		a) Entity Missing : Checks if the corresponding entity is present
 * 		b) Property removed : Informs about the same
 * 3) Entity's renamed : Checks for redirects and returns the new name. 
 * 4) In case of hybrid queries: use of homophones/wrong spellings!    
 * @author rricha
 * How to check if entity is missing or the property? 
 */

public class SparqlCorrection {
	// sparql endpoint URL
	String endpoint="http://dbpedia.org/sparql"; 
	
	private static final String[] BLACKLIST = { "UNION", "}"};
	
	/**
	 * This function is called if property is missing 
	 * @param prefixString
	 * @param subject
	 * @param predicate
	 * @param object
	 * @return
	 */
	public String isEntityPresent(String prefixString, String subject, String predicate, String object) {
		String entityToBeChecked = subject; 
		String query = prefixString;
		if (subject.startsWith("?") && !object.startsWith("?")) {
			entityToBeChecked = object;
			query += "ASK { VALUES (?r) { ("+ entityToBeChecked+ " ) } { ?r ?p ?o } UNION { ?s ?r ?o } UNION { ?s ?p ?r }} ";
		}
		else if (object.startsWith("?") && !subject.startsWith("?")){
		 query +=  "ASK { VALUES (?r) { ("+ entityToBeChecked+ " ) } { ?r ?p ?o } UNION { ?s ?r ?o } UNION { ?s ?p ?r }} ";
		}
		else 
			return null;
		//System.out.println(query);
		QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, query); //put query to jena sparql library
		boolean rs = qe.execAsk(); //execute query
		
		if (rs == true) {
			return null;
		}
		
		return entityToBeChecked;
	}
	
	//case: property change 
	public List<String> findNewProperty(String queryString) throws ParseException{
		List<String> changedTriples = new ArrayList<>();
		String newTriple = new String();
		int prefixEnd = 0;
		String queryStringLowercased = queryString.toLowerCase();
		
		if (queryStringLowercased.contains("select"))
			prefixEnd = queryStringLowercased.indexOf("select");
		else 
			prefixEnd = queryStringLowercased.indexOf("ask");
			
		String prefixString = queryString.substring(0, prefixEnd);
		String alternatePrefixString = new String(); //in case the query has no prefix 
		//System.out.println(prefixString);
		
		//extract triples from the SPARQL query
		String triples = queryString.substring(queryString.indexOf("{")+1, queryString.lastIndexOf("}"));
		
		//remove everything that can't be spo
		for (String blacklisted : BLACKLIST) { 
			triples = triples.replace(" " + blacklisted + " ", " ").trim();
			//System.out.println(triples);
			}
		if (triples.contains("FILTER")) {
			int removeFrom = triples.indexOf("FILTER");
			triples = triples.substring(0, removeFrom);
			//System.out.println(triples);
		}
		
		String[] splitTriples = triples.split("\\s+[\\.]\\s+|\\s+[\\.]|\\; |\\s+[\\{]\\s+|OPTIONAL ");
		String subject = new String();
		String predicate = new String(), object = new String();
		
		//extract subject, predicate and object from the triple 
		for (int i= 0; i < splitTriples.length ; i++) {
			splitTriples[i] = splitTriples[i].replace("{", "").trim();
			//System.out.println(splitTriples[i]);
			String modifiedQuery = prefixString + " select distinct ?p where { ";
			String[] words = splitTriples[i].split("\\s+");
			
			//the triple just has s,p,o and either s or o is unknown
			if (words.length == 3 && ((words[0].startsWith("?") && !words[2].startsWith("?")) || (!words[0].startsWith("?") && words[2].startsWith("?")))  ) {  
				subject = words[0];
				predicate = words[1];
				object = words[2];
				modifiedQuery = modifiedQuery + subject + " ?p " + object + " }";
				System.out.println(modifiedQuery);
			}
			
			//subject remains the same as before, so add it in the triple as well.
			else if (words.length ==2 && ((subject.startsWith("?") && !words[1].startsWith("?") || (!subject.startsWith("?") && words[1].startsWith("?")))) ) { 
				predicate = words[0];
				object = words[1];
				modifiedQuery = modifiedQuery + subject + " ?p " + object + " }";
				splitTriples[i] = subject + " " + splitTriples[i];
				System.out.println(modifiedQuery + "\n" + splitTriples[i]);
			}
			
			//if both s and o unknown, include the previous splitTriple
			else if (words.length == 3 && i > 0) {
				subject = words[0];
				predicate = words[1];
				object = words[2];
				modifiedQuery = modifiedQuery + splitTriples[i-1] + " . " + subject + " ?p " + object + " }";
				System.out.println(modifiedQuery);
			}
			else if (words.length == 2 && i > 0) {
				predicate = words[0];
				object = words[1];
				modifiedQuery = modifiedQuery + splitTriples[i-1] + " . " + subject + " ?p " + object + " }";
				splitTriples[i] = subject + " " + splitTriples[i];
				System.out.println(modifiedQuery + "\n" + splitTriples[i]);
			}
			
			//extract that string from the predicate which has to be matched against the results from sparql endpoint
			int propertyBeginsAt = 0;
			String predMatch = new String();
			// for preds like <http://dbpedia.org/ontology/ground>
			if (predicate.startsWith("<")) {
				propertyBeginsAt = predicate.lastIndexOf("/");
				predMatch = predicate.substring(propertyBeginsAt+1, predicate.length()-1);
				alternatePrefixString = predicate.substring(1, propertyBeginsAt+1);
				//System.out.println(alternatePrefixString);
			}
			//for preds with abbrev. (rdf:type)
			else {
				propertyBeginsAt = predicate.indexOf(":");
				predMatch = predicate.substring(propertyBeginsAt+1);
			}
			//System.out.println(predMatch);
			
			QueryExecution qe = QueryExecutionFactory.sparqlService(endpoint, modifiedQuery); //put query to jena sparql library
			ResultSet rs = qe.execSelect(); //execute query
			List<String> result = new ArrayList<>();
			
			while (rs.hasNext()) {
                 QuerySolution s = rs.nextSolution();     
                 Iterator<String> varNames = s.varNames();
                 for (Iterator<String> it = varNames; it.hasNext(); ) {
                     String varName = it.next();
                     //adds to result only if the value contains predMatch string
                     if (s.get(varName).toString().contains(predMatch)) {
                    	 result.add(s.get(varName).toString());
                     }
                 }
			}
			System.out.println(result);
			
			Boolean prefixFound = false;
			//analyze the result obtained
            if(result.isEmpty()) { //property missing case
            	//check if entity is missing/entity's name has changed
            	String entityMissing = isEntityPresent(prefixString, subject, predicate, object); 
            	if(entityMissing != null) {
            		changedTriples.add("The entity " + entityMissing + " is missing in " + splitTriples[i]);
            		break;
            	}
            	else {
            		changedTriples.add("The predicate " + predicate + " is missing in " + splitTriples[i]);
            	}
            	
            }
            else {
            	for (String str : result) {
            		int predAt = str.indexOf(predMatch);
            		String prefix = str.substring(0, predAt);
            		
            		if (!prefixString.isEmpty() && prefixString.contains(prefix) || !alternatePrefixString.isEmpty() && alternatePrefixString.equals(prefix)) {
            			//prefix found!!
            			prefixFound = true;
            			break;
            		}
//            		//if the prefixes don't match, there is a change in the property
//            		else if  (!prefixString.isEmpty() && !prefixString.contains(prefix)) {
//            			changedTriples.add(subject + " " + str + " " + object);
//            		}
//            		else if (!alternatePrefixString.isEmpty() && !alternatePrefixString.equals(prefix)) {
//            			changedTriples.add(subject + " " + str + " " + object);
//            		}
            	}
            	if (prefixFound == false) {
            		for (String str: result) {
            			int predAt = str.indexOf(predMatch);
                		String prefix = str.substring(0, predAt);
                		
            			if  (!prefixString.isEmpty() && !prefixString.contains(prefix)) {
                			changedTriples.add(subject + " " + str + " " + object);
                		}
                		else if (!alternatePrefixString.isEmpty() && !alternatePrefixString.equals(prefix)) {
                			changedTriples.add(subject + " " + str + " " + object);
                		}
            		}
            	}
                	
            }
            
		}
		System.out.println("The Suggestion is "+changedTriples);
		return changedTriples;
	}
	
	public static void main(String[] args) throws ParseException {
		SparqlCorrection sc = new SparqlCorrection();
		//String queryString = "SELECT DISTINCT ?n WHERE { <http://dbpedia.org/resource/FC_Porto> <http://dbpedia.org/ontology/ground> ?x . ?x <http://dbpedia.org/ontology/seatingCapacity> ?n .}";
		String queryString = "PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX  onto: <http://dbpedia.org/ontology/> SELECT  ?date WHERE { ?website  rdf:type          onto:Software ; onto:releaseDate  ?date ;              rdfs:label        \"DBpedia\"@en }";
		//String queryString = "PREFIX res: <http://dbpedia.org/resource/> select distinct ?s ?x where {  res:New_Delhi dbo:country ?s ; dbo:areaCode ?x .}";
		//String queryString = "PREFIX dbo: <http://dbpedia.org/ontology/>PREFIX dbp: <http://dbpedia.org/property/>PREFIX res: <http://dbpedia.org/resource/>PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>SELECT DISTINCT ?uriWHERE {        ?uri rdf:type dbo:Film .        ?uri dbo:director res:Akira_Kurosawa .      { ?uri dbo:releaseDate ?x . }       UNION       { ?uri dbp:released ?x . }        res:Rashomon dbo:releaseDate ?y .        FILTER (?y > ?x)}";
		//String queryString = "PREFIX  dbpedia2: <http://dbpedia.org/property/> PREFIX  res:  <http://dbpedia.org/resource/> SELECT  ?date WHERE { res:Germany  dbpedia2:accessioneudate  ?date }";
		//String queryString = "PREFIX  yago: <http://dbpedia.org/class/yago/> PREFIX  res:  <http://dbpedia.org/resource/> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX  onto: <http://dbpedia.org/ontology/> SELECT DISTINCT  ?uri ?string WHERE { ?uri  rdf:type  res:European_Union ; onto:governmentType  res:Constitutional_monarchy OPTIONAL { ?uri  rdfs:label  ?string FILTER ( lang(?string) = 'en' ) } }";
		//String queryString = "PREFIX  yago: <http://dbpedia.org/class/yago/> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> SELECT DISTINCT  ?uri ?string WHERE { ?uri  rdf:type  yago:BirdsOfTheUnitedStates  OPTIONAL ?uri  rdfs:label  ?string FILTER ( lang(?string) = \"en\" ) } }";
		//String queryString = "PREFIX  yago: <http://dbpedia.org/class/yago/> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#>  PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> PREFIX  prop: <http://dbpedia.org/property/>  SELECT  ?uri ?string WHERE { ?uri  rdf:type     yago:FemaleHeadsOfGovernment ; prop:office  ?office FILTER regex(?office, \"Chancellor of Germany\")OPTIONAL{ ?uri  rdfs:label  ?stringFILTER ( lang(?string) = \"en\" )}}";
		//String queryString = "PREFIX  yago: <http://dbpedia.org/class/yago/> PREFIX  rdfs: <http://www.w3.org/2000/01/rdf-schema#> PREFIX  dbpedia2: <http://dbpedia.org/property/> PREFIX  rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> SELECT  ?uri ?string WHERE { ?uri  rdf:type  yago:StatesOfTheUnitedStates ; dbpedia2:densityrank  ?density    OPTIONAL      { ?uri  rdfs:label  ?string       FILTER ( lang(?string) = \"en\" )      }  } ORDER BY ASC(?density) LIMIT   1";
		System.out.println("The suggestion is this "+sc.findNewProperty(queryString));
	}
}