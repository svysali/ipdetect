package com.ecse611.ipdetect;

import java.util.ArrayList;
import java.util.List;

import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.RepositoryFile;
import org.repodriller.scm.SCMRepository;

import com.google.common.graph.MutableValueGraph;
import com.google.common.graph.ValueGraphBuilder;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class SpoonParserVisitor implements CommitVisitor {
	private MutableValueGraph<String, Float> commit_graph; 
	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
		Launcher launcher = new Launcher();
		try {
			repo.getScm().checkout(commit.getHash());
			System.out.println("Commit" + commit.getHash());
			List<RepositoryFile> files = repo.getScm().files();
			for(RepositoryFile file : files) {
				if(!file.fileNameEndsWith("java")) continue;
				launcher.addInputResource(file.getFile().getPath());
			}
			launcher.buildModel();
			CtModel model = launcher.getModel();

			this.commit_graph = ValueGraphBuilder.directed().build();

			//First get a list of all classes defined in our source.This can be used to filter out any library methods
			ArrayList<String> classes = new ArrayList<String>();
			for(CtType<?> type:model.getAllTypes()) {
				classes.add(type.getQualifiedName());
			}

			//Then go class by class to get other details we need
			for(CtType<?> type:model.getAllTypes()) {
				System.out.println("class " + type.getQualifiedName());
				//Go through superclasses/interfaces can collect methods that can be possible overriden by methods in this class
				ArrayList<CtMethod> possible_overrides = new ArrayList<CtMethod>();

				if(type.getSuperclass() != null){
					if(classes.contains(type.getSuperclass().getQualifiedName())) {
						
						possible_overrides.addAll(type.getSuperclass().getDeclaration().getMethods());
					}
				}	
				for(CtTypeReference<?> intf:type.getSuperInterfaces()){
					if(classes.contains(intf.getQualifiedName())) {
						possible_overrides.addAll(intf.getDeclaration().getMethods());
					}
				}
				List<String> method_names = new ArrayList<String>();

				//Iterate through each method and collect details
				for(CtMethod<?> method:type.getMethods()) {
					String current_method = getQSignature(method);
					this.commit_graph.addNode(current_method);
					method_names.add(current_method);
					//Iterate through each method call
					List<CtInvocation> method_calls = method
							.filterChildren(new TypeFilter(CtInvocation.class))
							.list();					
					for(CtInvocation mc:method_calls) {
						CtExecutableReference method_executable = mc.getExecutable();

						//We are only interested in our own methods, not java lib methods.
						if(classes.contains(method_executable.getDeclaringType().getQualifiedName())) {
							String called_method = getQSignature(method_executable);
							this.commit_graph.addNode(called_method);
							this.commit_graph.putEdgeValue(current_method, called_method,Weight.CALLING);
							this.commit_graph.putEdgeValue(called_method, current_method,Weight.CALLED);
						}
					}
					for(CtMethod po_method:possible_overrides) {
						if(method.isOverriding(po_method)) {
							//System.out.println("\t\to "+po_method.getSimpleName()+"::"+po_method.getDeclaringType().getQualifiedName());
							String overriden_method = getQSignature(po_method);
							this.commit_graph.addNode(overriden_method);
							this.commit_graph.putEdgeValue(overriden_method, current_method,Weight.CALLING);
							this.commit_graph.putEdgeValue(current_method, overriden_method,Weight.CALLED);
						}
					}	
				}

				//Add the Edef edges
				for (int i = 0; i < method_names.size(); i++) {
					for (int j = i+1; j < method_names.size(); j++) {
						this.commit_graph.putEdgeValue(method_names.get(i),method_names.get(j),Weight.DEF);
						this.commit_graph.putEdgeValue(method_names.get(j),method_names.get(i),Weight.DEF);
					}
				}
			}
			
			for(String node:this.commit_graph.nodes()) {
				System.out.println(node);
			}
		} catch(Exception E) {
			System.out.println(E.toString());
		} 
		finally {
			repo.getScm().reset();
		}
	}
	
	
	private String getQSignature(CtMethod m) {
		return m.getDeclaringType().getQualifiedName()+"#"+m.getSignature();
	}

	private String getQSignature(CtExecutableReference e) {
		return e.getDeclaringType().getQualifiedName()+"#"+e.getSignature();
	}
	
	@Override
	public String name() {
		return "java-parser";
	}
}

class Weight {
	public static final float SAME = 1;
	public static final float CALLING = 3/14;
	public static final float CALLED = 3/14;
	public static final float DEF = 3/7;
}
