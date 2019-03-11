package com.ecse611.ipdetect;
import com.ecse611.ipdetect.Weight;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;


import org.apache.log4j.Logger;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.repodriller.scm.GitRepository;
import org.repodriller.scm.SCM;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import spoon.Launcher;
import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import com.ecse611.ipdetect.Config;

public class IPDetector {
	private static final Logger logger = Logger.getLogger(IPDetector.class);
	private static String repos_root = Config.REPO_ROOT;
	private static String project = Config.PROJECT;

	public static void main(String[] args) throws FileNotFoundException, IOException {
		SCM repo = null;
		try {		
			repo = new GitRepository(repos_root+project);
		} catch(Exception E) {
			logger.fatal("Error initialising SCM repository. Quitting.");
			System.exit(1);
		}

		/*summarize("accumulo.csv");
		summarize("ambari.csv");
		summarize("cayenne.csv");*/


		Multimap<String, CommitObj> work_id_to_commits_map = null;
		String output_csv = new StringBuilder().append(project).append("_results.csv").toString();
		BufferedWriter writer = null;
		CSVPrinter csvPrinter = null;
				

		try {	
			work_id_to_commits_map =	generateIPPairs(project+".csv");
		} catch(Exception e) {
			logger.fatal("Could not generate IP pairs for analysis");
			System.exit(1);
		}	

		try { 
			writer = Files.newBufferedWriter(Paths.get(output_csv));
			csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
	                .withHeader("ID", "X", "Y", "Score","isIP"));
		} catch(Exception e) {
			logger.fatal("Could not create output file");
			System.exit(1);
		}

		int count =0;
		Scanner s = new Scanner(new File("cayenne_results/cc_aa"));
		ArrayList<String> work_id_tbp = new ArrayList<String>();
		while (s.hasNext()){
			work_id_tbp.add(s.next());
		}
		s.close();
		for(String work_id:work_id_tbp) {
			if(count++ < Config.START_ROW) {
				continue;
			}
			try {
				List<CommitObj> commit_list = new ArrayList<CommitObj>(work_id_to_commits_map.get(work_id));
				if(commit_list.size() <= 1) {
					logger.info("WORK-ID: " + work_id + " has <=1 commit. Skipping.");
					continue;
				}
				logger.info(new StringBuilder().append("WORK-ID: ").append(work_id).append(" processing ").toString());
				Collections.sort(commit_list,new SortbyTS());
				for (int i = 0; i < (commit_list.size()-1); i++) {
					String c_old =  commit_list.get(i).getHash();
					
					List<String> mod_x = getMethodsModifiedAtCommit(repo,c_old);
					if(mod_x.size() > 0 && mod_x.size() <= 50 ) {
						for (int j = i+1; j < commit_list.size(); j++) {
							double score = 0.0;
							String c_new = commit_list.get(j).getHash();
							List<String> mod_y = getMethodsModifiedAtCommit(repo,c_new);
							if(mod_y.size() > 0 && mod_y.size() <=50) {
								score = getIPScore(project,c_old,c_new,mod_x,mod_y);
							}	
							boolean isIP = (score >= Weight.THRESHOLD) ? true : false;
							csvPrinter.printRecord(work_id,c_old,c_new,score,isIP);  
						}
					} else {
						for (int j = i+1; j < commit_list.size(); j++) {
							csvPrinter.printRecord(work_id,c_old,commit_list.get(j).getHash(),0,false);  
						}

					}	
				}

			} catch(Exception E) {
				StringBuilder sb = new StringBuilder();
				sb.append(work_id);
				sb.append(work_id);
				sb.append(":");
				sb.append(E.getMessage());
				logger.error(sb.toString());
			}
			csvPrinter.flush();
			if(count == Config.END_ROW) {
				break;
			}
		}
		
			if(writer != null) {
			writer.close();
		}

	}

	public static List<String> getMethodsModifiedAtCommit(SCM repo,String commit) throws JsonSyntaxException, JsonIOException, FileNotFoundException {
		List<String> mod_methods = new ArrayList<String>();
		String commit_json_file = new StringBuilder().append(project).append("/").append(commit).append(".json").toString(); 
		File commit_file = new File(commit_json_file); 
		if(!commit_file.exists()){
			generateJsonForCommit(repo,commit);
		}
		String parent_commit = repo.getCommit(commit).getParents().get(0);
		String parent_commit_json_file = new StringBuilder().append(project).append("/").append(parent_commit).append(".json").toString(); 
		File parent_commit_file = new File(parent_commit_json_file); 
		if(!parent_commit_file.exists()){
			generateJsonForCommit(repo,parent_commit);
		}
		if(!(commit_file.exists() && parent_commit_file.exists())){
			logger.warn("Could not compute modified methods for : " +  commit);
			return mod_methods;
		}
		Map<String, Integer> method_map_commit = getMethodHashMap(commit_json_file);
		Map<String, Integer> method_map_parent = getMethodHashMap(parent_commit_json_file);
		for(String method: method_map_commit.keySet()) {
			if(method_map_parent.containsKey(method)) {
				if(method_map_commit.get(method).intValue() != method_map_parent.get(method).intValue()) {
					mod_methods.add(method);
				}
			}
		}
		return mod_methods;
	}

	private static JsonArray getJsonArray(String jsonFilePath) throws JsonSyntaxException, JsonIOException, FileNotFoundException {
		Gson gson = new Gson();
		File jsonFile = Paths.get(jsonFilePath).toFile();
		JsonArray json = gson.fromJson(new FileReader(jsonFile), JsonArray.class);
		return json;
	}

	private static Map<String, Integer> getMethodHashMap(String commitJsonFile) throws JsonSyntaxException, JsonIOException, FileNotFoundException {
		Map<String, Integer> method_map = new HashMap<String,Integer>();
		for(JsonElement c:getJsonArray(commitJsonFile)) {
			String current_class = c.getAsJsonObject().get("class_name").getAsString();
			for(JsonElement method:c.getAsJsonObject().get("methods").getAsJsonArray()) {
				JsonObject m = method.getAsJsonObject();
				StringBuilder sb = new StringBuilder();
					sb.append(current_class);
					sb.append("#");
					sb.append(m.get("method_name").getAsString());
				String current_method = sb.toString();
				method_map.put(current_method,m.get("hashcode").getAsInt());
			}
		}
		return method_map;
	}


	public static void generateJsonForCommit(SCM repo,String commit) {
		String file_name = project + "/" + commit + ".json"; 
		if(new File(file_name).exists()) {
			return;
		}
		logger.info("Generating json for: " + commit);
		CtModel model = null;
		MavenLauncher mlauncher = null;
		Launcher launcher = null;
		try {
			repo.checkout(commit);
			String source_dir = repo.info().getPath();

			//if((new File(source_dir+"/pom.xml").exists())) {
			try {
				mlauncher = new MavenLauncher(source_dir, MavenLauncher.SOURCE_TYPE.ALL_SOURCE);
				mlauncher.getEnvironment().setIgnoreDuplicateDeclarations(true);
				mlauncher.buildModel();
				model = mlauncher.getModel();
			} catch (Exception e) {
				try {
					launcher = new Launcher();
					launcher.addInputResource(source_dir);
					launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
					launcher.buildModel();
					model = launcher.getModel();
				} catch (Exception E) {
					throw new Exception("Both launchers did not work");
				}	 
			}

			JsonFactory jfactory = new JsonFactory();
			JsonGenerator jGenerator = jfactory.createGenerator(new File(file_name), JsonEncoding.UTF8);

			//First get a list of all classes defined in our source.This can be used to filter out any library methods
			ArrayList<String> classes = new ArrayList<String>();
			for(CtType<?> type:model.getAllTypes()) {
				classes.add(type.getQualifiedName());
			}
			//Then go class by class to get other details we need
			jGenerator.writeStartArray();
			for(CtType<?> type:model.getAllTypes()) {
				try {
					jGenerator.writeStartObject();
					jGenerator.writeStringField("class_name", type.getQualifiedName());
					jGenerator.writeFieldName("methods");
					jGenerator.writeStartArray();
					
					//Iterate through each method and collect details
					for(CtMethod<?> method:type.getMethods()) { 
						jGenerator.writeStartObject();
						jGenerator.writeStringField("method_name", method.getSignature());
						jGenerator.writeNumberField("hashcode", method.toString().hashCode());
						//Iterate through each method call

						@SuppressWarnings({ "unchecked", "rawtypes" })
						List<CtInvocation> method_calls = method
							.filterChildren(new TypeFilter(CtInvocation.class))
							.list();					

						jGenerator.writeFieldName("calls"); // "messages" :
						jGenerator.writeStartArray();
						
						//Doesn't seem to be working for nested method calls
						for(CtInvocation<?> mc:method_calls) {
							try {
								CtExecutableReference<?> method_executable = mc.getExecutable();

								if(method_executable != null) {
									CtTypeReference<?> declaring_class = method_executable.getDeclaringType();
								//We are only interested in our own methods, not java lib methods.
									if(classes.contains(declaring_class.getQualifiedName())) {							
										jGenerator.writeString(getQSignature(method_executable));
									}	
								}	
							} catch(NullPointerException e) {
							//
							}	
						}
						jGenerator.writeEndArray();
						try {
							CtExecutableReference<?> overrides = method.getReference().getOverridingExecutable();
							if(overrides != null) {
								if(classes.contains(overrides.getDeclaringType().getQualifiedName())) {
									jGenerator.writeStringField("overrides", getQSignature(overrides));
								}
							}
						}catch(Exception E){
							logger.warn("Commit " + commit + ":" + method.getReference().getPath().toString() + " overrides could not be processed");
						}	
						jGenerator.writeEndObject();
					}//End of method for loop
					jGenerator.writeEndArray();
					jGenerator.writeEndObject();
				} catch(Exception e) {
					logger.error("Could not process class : " + type);
				}
			}//End of classes for loop	
			jGenerator.writeEndArray();
			jGenerator.close();
		} catch(Exception E) {
			logger.error("Commit " + commit + " " +E.getMessage());
		} finally {
			repo.reset();
		}
	}

	private static String getQSignature(CtExecutableReference<?> e) {
		return e.getDeclaringType().getQualifiedName()+"#"+e.getSignature();
	}

	public static double getIPScore(String project, String c_old ,String c_new,List<String> mod_x,List<String> mod_y) throws JsonProcessingException, IOException {
		double score = 0.0;
		double Sx = 0.0;
		double Sy = 0.0;
		if(!(mod_x.isEmpty() || mod_y.isEmpty())) {
			DefaultWeightedEdge e = new DefaultWeightedEdge();
			Graph<String, DefaultWeightedEdge> old_graph = getGraphFromJson(project + "/" + c_old + ".json","");
			Graph<String, DefaultWeightedEdge> new_graph = getGraphFromJson(project + "/" +c_new + ".json","n__");
			//Combine with second graph and add E_Same edges
			Graphs.addGraph(new_graph, old_graph);
			
			for(String vertex:old_graph.vertexSet()) {
				String new_vertex = "n__"+vertex;
				if(new_graph.vertexSet().contains(new_vertex)) {
					e = new_graph.addEdge(new_vertex, vertex);
					if(e != null) { new_graph.setEdgeWeight(e, Weight.SAME);}
					e = new_graph.addEdge(vertex, new_vertex);
					if(e != null) { new_graph.setEdgeWeight(e, Weight.SAME);}
				} 
			}

			DijkstraShortestPath<String, DefaultWeightedEdge> dijkstraAlg =
					new DijkstraShortestPath<>(new_graph);
			/* Compute reciprocals for both commit x(old) and commit y(new)
			 * 1) For each method Mx modified in commit x
			 *   	Find the nearest method My modified in commit y
			 * 2) Sx = average (reciprocal(nearest_method))
			 * 3) For each method My modified in commit y
			 *   	Find the nearest method Mx modified in commit x 
			 * 4) Sy = average (reciprocal(nearest_method))
			 * 5) Score is the maximum of average of Sx,Sy and Sy.  
			 */
			for(String Mx:mod_x) {
				double nearest = Double.POSITIVE_INFINITY;
				for(String My:mod_y) {
					nearest = Math.min(nearest, dijkstraAlg.getPathWeight(Mx, "n__"+My));
				}
				Sx+=(1/nearest);
			}
			Sx = Sx/mod_x.size();

			for(String My:mod_y) {
				double nearest = Double.POSITIVE_INFINITY;
				for(String Mx:mod_x) {
					nearest = Math.min(nearest, dijkstraAlg.getPathWeight("n__"+My,Mx));
				}
				Sy+=(1/nearest);
			}
			Sy = Sy/mod_y.size();

			// = (max(1/2(Sx+Sy)),Sy)
			score = Math.max(((Sx+Sy)/2), Sy);
		} 	
		return score;
	}

	private static Graph<String,DefaultWeightedEdge> getGraphFromJson(String jsonFilePath,String prefix) throws JsonSyntaxException, JsonIOException, FileNotFoundException	{
		Graph<String, DefaultWeightedEdge> g = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
		JsonArray json = getJsonArray(jsonFilePath);
		DefaultWeightedEdge e = new DefaultWeightedEdge(); 
		for(JsonElement c:json) {
			String current_class = c.getAsJsonObject().get("class_name").getAsString();
			ArrayList<String> methods_list = new ArrayList<String>();
			for(JsonElement method:c.getAsJsonObject().get("methods").getAsJsonArray()) {
				JsonObject m = method.getAsJsonObject();
				String current_method = prefix+current_class+"#"+m.get("method_name").getAsString();
				g.addVertex(current_method);
				methods_list.add(current_method);
				for(JsonElement method_call:m.get("calls").getAsJsonArray()) {
					String called_method = prefix + method_call.getAsString();
					g.addVertex(called_method);
					e = g.addEdge(current_method, called_method);
					if(e != null) { g.setEdgeWeight(e, Weight.CALLED);}
					e = g.addEdge(called_method, current_method);
					if(e != null) { g.setEdgeWeight(e, Weight.CALLING);}
				}	
				if(m.has("overrides")) {
					String overridden_method = prefix + m.get("overrides").getAsString(); 
					g.addVertex(overridden_method );
					e = g.addEdge(current_method, overridden_method);
					if(e != null) { g.setEdgeWeight(e, Weight.CALLED);}
					e = g.addEdge(overridden_method, current_method);
					if(e != null) { g.setEdgeWeight(e, Weight.CALLING);}
				}
			}
			for (int i = 0; i < methods_list.size(); i++) {
				for (int j = i+1; j < methods_list.size(); j++) {
					e = g.addEdge(methods_list.get(i), methods_list.get(j));
					if(e != null) { g.setEdgeWeight(e, Weight.DEF);}
					e = g.addEdge(methods_list.get(j), methods_list.get(i));
					if(e != null) { g.setEdgeWeight(e, Weight.DEF);}
				}
			}
		}
		return g;
	}

	public static Multimap<String, CommitObj> generateIPPairs(String inputcsv) throws FileNotFoundException, IOException {
		Multimap<String, CommitObj> work_id_to_commits_map = ArrayListMultimap.create();

		try (BufferedReader br = new BufferedReader(new FileReader(inputcsv))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				if(values.length > 2) {
					work_id_to_commits_map.put(values[2], new CommitObj(values[0],Long.parseLong(values[1])));
				} 
			}
		} catch(Exception E) {
			logger.fatal("Could not convert csv to commit pair map");
		}
		return work_id_to_commits_map ;
	}

	public static void summarize(String inputcsv) throws FileNotFoundException, IOException {
		Multimap<String, CommitObj> work_id_to_commits_map = ArrayListMultimap.create();
		Integer total_commits = 0;
		Integer commits_wo_workid = 0;
		try (BufferedReader br = new BufferedReader(new FileReader(inputcsv))) {
			String line;
			while ((line = br.readLine()) != null) {
				total_commits+=1;
				String[] values = line.split(",");
				if(values.length > 2) {
					work_id_to_commits_map.put(values[2], new CommitObj(values[0],Long.parseLong(values[1])));
				} else {
					commits_wo_workid+=1;
				} 
			}
		} catch(Exception E) {
			System.out.println("Could not convert csv to commit pair map");
		}
		System.out.println("-----------------" + inputcsv + "-----------------");
		System.out.println("Total Commits \t\t:\t"+total_commits);
		System.out.println("Commits WO ID \t\t:\t"+commits_wo_workid);
		System.out.println("Percentage \t\t:\t"+((float)(total_commits-commits_wo_workid)/total_commits));
		System.out.println("Unique Work-IDs \t:\t"+ work_id_to_commits_map.keySet().size());
		float work_id_ip = 0;
		Integer included_commits = 0;
		for(String work_id:work_id_to_commits_map.keySet()) {
			if(work_id_to_commits_map.get(work_id).size() > 1) {
				work_id_ip+=1;
				included_commits+=work_id_to_commits_map.get(work_id).size();
			}
		}
		System.out.println("Work_ID IP \t\t:\t"+work_id_ip);
		System.out.println("Work_ID Percentage \t:\t"+ (float)(work_id_ip/work_id_to_commits_map.keySet().size()));
		System.out.println("Commits to be analyzed\t:\t"+ included_commits);
	}
}

class CommitObj{
	private String hash;
	private Long timestamp;
	public CommitObj(String h,Long t) {
		this.hash = h;
		this.timestamp = t;
	}
	public String getHash() {
		return this.hash;
	}

	public Long getTS() {
		return this.timestamp;
	}

	@Override
	public boolean equals(Object obj) {
	    if (obj == null) return false;
	    if (!(obj instanceof CommitObj))
	        return false;
	    if (obj == this)
	        return true;
	    return this.hash == ((CommitObj) obj).getHash();
	}
	
	@Override
	public int hashCode() {
	    return hash.hashCode();
	}

}

class SortbyTS implements Comparator<CommitObj> 
{ 
	public int compare(CommitObj a, CommitObj b) 
	{ 
		return (int) (a.getTS() - b.getTS()); 
	} 
} 
