package com.ecse611.ipdetect;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.internal.resources.Project;
import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

import spoon.MavenLauncher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class SpoonParserVisitor implements CommitVisitor {
	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
		try {
			repo.getScm().checkout(commit.getHash());
			System.out.println("Commit" + commit.getHash());
			String source_dir = repo.getPath();
			MavenLauncher launcher = new MavenLauncher(source_dir, MavenLauncher.SOURCE_TYPE.APP_SOURCE);
			launcher.buildModel();
			CtModel model = launcher.getModel();

			JsonFactory jfactory = new JsonFactory();
			JsonGenerator jGenerator = jfactory.createGenerator(new File(
					"accumulo"+"/"+commit.getHash()+".json"), JsonEncoding.UTF8);
			
			//First get a list of all classes defined in our source.This can be used to filter out any library methods
			ArrayList<String> classes = new ArrayList<String>();
			for(CtType<?> type:model.getAllTypes()) {
				classes.add(type.getQualifiedName());
			}

			//Then go class by class to get other details we need
			for(CtType<?> type:model.getAllTypes()) {

				//Go through superclasses/interfaces can collect methods that can be possible overriden by methods in this class
				ArrayList<CtMethod<?>> possible_overrides = new ArrayList<CtMethod<?>>();

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
				jGenerator.writeStartObject();
				jGenerator.writeStringField("name", type.getQualifiedName());
				jGenerator.writeFieldName("methods");
				jGenerator.writeStartArray();
				//Iterate through each method and collect details
				for(CtMethod<?> method:type.getMethods()) { 
					jGenerator.writeStartObject();
					jGenerator.writeStringField("name", method.getSignature());
					//Iterate through each method call
					@SuppressWarnings({ "unchecked", "rawtypes" })
					List<CtInvocation> method_calls = method
					.filterChildren(new TypeFilter(CtInvocation.class))
					.list();					
					
					jGenerator.writeFieldName("calls"); // "messages" :
					jGenerator.writeStartArray();
					for(CtInvocation<?> mc:method_calls) {
						CtExecutableReference<?> method_executable = mc.getExecutable();
						//We are only interested in our own methods, not java lib methods.
						if(classes.contains(method_executable.getDeclaringType().getQualifiedName())) {							
							jGenerator.writeString(getQSignature(method_executable));
						}
					}
					jGenerator.writeEndArray();

					jGenerator.writeFieldName("overrides"); // "messages" :
					jGenerator.writeStartArray();
					for(CtMethod<?> po_method:possible_overrides) {
						if(method.isOverriding(po_method)) {
							jGenerator.writeString(getQSignature(po_method));
						}
					}
					jGenerator.writeEndArray();

					jGenerator.writeEndObject();
				}
				jGenerator.writeEndArray();
				jGenerator.writeEndObject();
			}
			jGenerator.close();
		} catch(Exception E) {
			E.printStackTrace();
		} 
		finally {
			repo.getScm().reset();
		}
	}


	private String getQSignature(CtMethod<?> m) {
		return m.getDeclaringType().getQualifiedName()+"#"+m.getSignature();
	}

	private String getQSignature(CtExecutableReference<?> e) {
		return e.getDeclaringType().getQualifiedName()+"#"+e.getSignature();
	}

	@Override
	public String name() {
		return "java-parser";
	}
}


