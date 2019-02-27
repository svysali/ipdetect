package com.ecse611.ipdetect;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.RepositoryFile;
import org.repodriller.scm.SCMRepository;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class SpoonParserVisitor implements CommitVisitor {

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
			//First get a list of all classes defined in our source.This can be used to filter out any default methods
			ArrayList<String> classes = new ArrayList<String>();
			for(CtType<?> type:model.getAllTypes()) {
				classes.add(type.getQualifiedName());
			}
			
			//Then go class by class to get other details we need
			for(CtType<?> type:model.getAllTypes()) {
				ArrayList<CtMethod> possible_overrides = new ArrayList<CtMethod>();
				System.out.println("* " + type.getQualifiedName());
				if(type.getSuperclass() != null){
					if(classes.contains(type.getSuperclass().getQualifiedName())) {
						possible_overrides.addAll(type.getSuperclass().getDeclaration().getMethods());
					}
				}	
				
				for(CtMethod<?> method:type.getMethods()) {
					System.out.println("\t+ " + method.getSimpleName());
					List<CtInvocation> method_calls = method.filterChildren(new TypeFilter(CtInvocation.class)).list();					
					for(CtInvocation mc:method_calls) {
						CtExecutableReference method_executable = mc.getExecutable();
						if(classes.contains(method_executable.getType().getQualifiedName())) {
							System.out.println("\t\tc "+method_executable.getSimpleName()+"::"+method_executable.getType());
						}
					}
					for(CtMethod po_method:possible_overrides) {
						if(method.isOverriding(po_method)) {
							System.out.println("\t\to "+po_method.getSignature()+"::"+po_method.getDeclaringType().getQualifiedName());
						}
					}	
				}
			}
		} catch(Exception E) {
			System.out.println("Error");
		} 
		finally {
			repo.getScm().reset();
		}
	}

	@Override
	public String name() {
		return "java-parser";
	}


}

