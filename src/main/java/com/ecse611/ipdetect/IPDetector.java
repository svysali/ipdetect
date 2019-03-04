package com.ecse611.ipdetect;
import com.ecse611.ipdetect.Weight;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


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
import com.google.common.collect.HashMultimap;
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

	public static void main(String[] args) {
		SCM repo = null;
		try {		
			repo = new GitRepository(repos_root+project);
		} catch(Exception E) {
			logger.fatal("Error initialising SCM repository. Quitting.");
		}	
		Long c_old_ts = null;
		Long c_new_ts = null;
		String c_old = null;
		String c_new= null;
		Integer count = 0;
		try {	
			Multimap<String, CommitObj> work_id_to_commits_map =	generateIPPairs(project+".csv");
			for(String work_id:work_id_to_commits_map.keySet()) {
				count+=1;
				System.out.println(work_id);
				try {
					List<CommitObj> commit_list = new ArrayList<CommitObj>(work_id_to_commits_map.get(work_id));
					Collections.sort(commit_list,new SortbyTS());
					if(commit_list.size() <= 1) {continue;}
					for (int i = 0; i < commit_list.size(); i++) {
						c_old =  commit_list.get(i).getHash();
						c_old_ts = commit_list.get(i).getTS();
						List<String> mod_x = getMethodsModifiedAtCommit(repo,c_old);
						Graph<String, DefaultWeightedEdge> old_graph = getGraphFromJson(project + "/" + c_old + ".json","");
						System.out.println("________________________________________________________________________________");
						System.out.println("MMAT " + c_old + " : " + c_old_ts  );
						for(String mod:mod_x) {
							System.out.println(mod);
						}

						for (int j = i+1; j < commit_list.size(); j++) {
							c_new = commit_list.get(j).getHash();
							c_new_ts = commit_list.get(j).getTS();
							System.out.println("MMAT NEW " + c_new + " : " + c_new_ts  );
							List<String> mod_y = getMethodsModifiedAtCommit(repo,c_new);
							Graph<String, DefaultWeightedEdge> new_graph = getGraphFromJson(project + "/" +c_new + ".json","");
							for(String mod:mod_y) {
								System.out.println(mod);
							}
							
							boolean isIP = isIPCommit(project,old_graph,new_graph,mod_x,mod_y);
							System.out.println(work_id +"," + c_old + "," + c_new + "," + isIP);
						}	
						
					}

				} catch(Exception E) {
					logger.error(work_id +"," + c_old + "," + c_new + ":" + E.getMessage());
				}
				//TBR: only for checking one whole loop.
				if(count==3) {
					break;
				}
			}
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static List<String> getMethodsModifiedAtCommit(SCM repo,String commit) throws JsonSyntaxException, JsonIOException, FileNotFoundException {
		List<String> mod_methods = new ArrayList<String>();
		String commit_json_file = project + "/" +commit + ".json"; 
		if(!(new File(commit_json_file).exists())){
			generateJsonForCommit(repo,commit);
		}
		String parent_commit = repo.getCommit(commit).getParents().get(0);
		String parent_commit_json_file = project + "/" +parent_commit + ".json";
		if(!(new File(parent_commit_json_file).exists())){
			generateJsonForCommit(repo,parent_commit);
		}
		Multimap<String, Integer> method_map_commit = getMethodHashMap(getJsonArray(commit_json_file));
		Multimap<String, Integer> method_map_parent = getMethodHashMap(getJsonArray(parent_commit_json_file));
		
		for(String method: method_map_commit.keySet()) {
			if(method_map_parent.containsKey(method)) {
				if(!method_map_parent.get(method).containsAll(method_map_commit.get(method))) {
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
	
	private static Multimap<String, Integer> getMethodHashMap(JsonArray json) {
		Multimap<String, Integer> method_map = HashMultimap.create();
		for(JsonElement c:json) {
			String current_class = c.getAsJsonObject().get("class_name").getAsString();
			for(JsonElement method:c.getAsJsonObject().get("methods").getAsJsonArray()) {
				JsonObject m = method.getAsJsonObject();
				String current_method = current_class+"#"+m.get("method_name").getAsString();
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

		try {
			repo.checkout(commit);
			String source_dir = repo.info().getPath();
			CtModel model = null;
			if((new File(source_dir+"/pom.xml").exists())) {
				MavenLauncher mlauncher = new MavenLauncher(source_dir, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
				mlauncher.buildModel();
				model = mlauncher.getModel();
			} else {
				Launcher launcher = new Launcher();
				launcher.addInputResource(source_dir);
				launcher.buildModel();
				model = launcher.getModel();
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
								//We are only interested in our own methods, not java lib methods.
								CtTypeReference<?> declaring_class = method_executable.getDeclaringType();


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
				}
				jGenerator.writeEndArray();
				jGenerator.writeEndObject();
			}
			jGenerator.writeEndArray();
			jGenerator.close();
		} catch(Exception E) {
			logger.error("Commit " + commit + " " +E.getMessage());
			E.getStackTrace();
		} 
		finally {
			repo.reset();
		}
	}

	private static String getQSignature(CtExecutableReference<?> e) {
		return e.getDeclaringType().getQualifiedName()+"#"+e.getSignature();
	}

	public static boolean isIPCommit(String project, Graph<String, DefaultWeightedEdge> old_graph,Graph<String, DefaultWeightedEdge> new_graph,List<String> mod_x,List<String> mod_y) throws JsonProcessingException, IOException {
		double score = 0.0;
		DefaultWeightedEdge e = new DefaultWeightedEdge();
		
		//Combine with second graph and add E_Same edges
		Graphs.addGraph(new_graph, old_graph);
		System.out.println("Number of vertices in combined: " + new_graph.vertexSet().size());
		Integer count = 0;
		for(String vertex:old_graph.vertexSet()) {
			String new_vertex = "n__"+vertex;
			if(new_graph.vertexSet().contains(new_vertex)) {
				count+=1;
				e = new_graph.addEdge(new_vertex, vertex);
				if(e != null) { new_graph.setEdgeWeight(e, Weight.SAME);}
				e = new_graph.addEdge(vertex, new_vertex);
				if(e != null) { new_graph.setEdgeWeight(e, Weight.SAME);}
			} 
		}

		System.out.println("no. of vertices with e_same edges: "+count);
		
		DijkstraShortestPath<String, DefaultWeightedEdge> dijkstraAlg =
	            new DijkstraShortestPath<>(new_graph);
		//Compute reciprocals and final value
		double Sx = 0.0;
		double Sy = 0.0;
		if(!(mod_x.isEmpty() || mod_y.isEmpty())) {
			//Calculate Sx
			for(String Mx:mod_x) {
				double nearest = 0.0;
				for(String My:mod_y) {
					nearest = Math.min(nearest, dijkstraAlg.getPathWeight(Mx, "n__"+My));
				}
				Sx+=(1/nearest);
			}
			Sx = Sx/mod_x.size();
			//Calculate Sy
			for(String My:mod_y) {
				double nearest = 0.0;
				for(String Mx:mod_x) {
					nearest = Math.min(nearest, dijkstraAlg.getPathWeight("n__"+My,Mx));
				}
				Sy+=(1/nearest);
			}
			Sy = Sy/mod_y.size();
			// = (max(1/2(Sx+Sy)),Sy)
			score = Math.max((0.5*(Sx+Sy)), Sy);
		}	
		//return result
		if(score >= Weight.THRESHOLD) {return true;}
		return false;
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

	public static Multimap<String,CommitObj> generateIPPairs(String inputcsv) throws FileNotFoundException, IOException {
		Multimap<String, CommitObj> work_id_to_commits_map = HashMultimap.create();
		
		try (BufferedReader br = new BufferedReader(new FileReader(inputcsv))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				if(values.length > 2) {
					work_id_to_commits_map.put(values[2], new CommitObj(values[0],Long.parseLong(values[1])));
				} 
			}
		} catch(Exception E) {
			System.out.println("Could not convert csv to commit pair map");
		}
		return work_id_to_commits_map;
	}	

	/* .replaceAll("\\(.*\\)", "") to replace signature with name,wait to hear back from the authors to confirm this */

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
	
	
}

class SortbyTS implements Comparator<CommitObj> 
{ 
	public int compare(CommitObj a, CommitObj b) 
    { 
        return (int) (a.getTS() - b.getTS()); 
    } 
} 
