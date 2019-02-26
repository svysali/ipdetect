package com.ecse611.ipdetect;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.RepositoryFile;
import org.repodriller.scm.SCMRepository;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.javaparsermodel.JavaParserFacade;
import com.github.javaparser.symbolsolver.javaparsermodel.declarations.JavaParserMethodDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;

public class JavaParserVisitor implements CommitVisitor {

	private TypeSolver typeSolver ;

	public JavaParserVisitor(TypeSolver ts) {
		this.setTypeSolver(ts);
	}

	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {

		try {
			repo.getScm().checkout(commit.getHash());
			System.out.println("Commit" + commit.getHash());
			List<RepositoryFile> files = repo.getScm().files();
			for(RepositoryFile file : files) {
				if(!file.fileNameEndsWith("java")) continue;

				File soFile = file.getFile();

				CallOrInterfaceVisitor  cvisitor = new CallOrInterfaceVisitor(this.getTypeSolver());
				try {
					ParseResult<CompilationUnit> cu = new JavaParser().parse(soFile);
					cvisitor.visit(cu.getResult().get().getTypes(), null);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
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

	public TypeSolver getTypeSolver() {
		return typeSolver;
	}

	public void setTypeSolver(TypeSolver typeSolver) {
		this.typeSolver = typeSolver;
	}

}


class CallOrInterfaceVisitor extends VoidVisitorAdapter<Void> {
	private TypeSolver typeSolver ;

	public CallOrInterfaceVisitor(TypeSolver ts) {
		this.setTypeSolver(ts);
	}

	public void visit(ClassOrInterfaceDeclaration n, Void arg) {
		super.visit(n, arg);
		System.out.println(" * " + n.getNameAsString() + 
				" extends " + n.getExtendedTypes().toString() +
				" implements " + n.getImplementedTypes().toString()
				);
		for(MethodDeclaration m: n.getMethods()) {
			System.out.println("\t++ " + m.getNameAsString());
			new MethodCallVisitor(this.getTypeSolver()).visit(m,null);
		}
	}

	public TypeSolver getTypeSolver() {
		return typeSolver;
	}

	public void setTypeSolver(TypeSolver typeSolver) {
		this.typeSolver = typeSolver;
	}
}

class MethodCallVisitor extends VoidVisitorAdapter<Void> {
	private TypeSolver typeSolver ;   
	public MethodCallVisitor(TypeSolver ts) {
		this.setTypeSolver(ts);
	}
	public void visit(MethodCallExpr mc, Void arg) {
		super.visit(mc, arg);
		try {
			ResolvedMethodDeclaration correspondingDeclaration = JavaParserFacade.get(this.getTypeSolver()).solve(mc).getCorrespondingDeclaration();
			if (correspondingDeclaration instanceof JavaParserMethodDeclaration) {
					System.out.println("\t>>>>"+mc.getName() + "->" + correspondingDeclaration.getQualifiedName());
			}
		} catch(Exception E) {
			//System.out.println(E.toString());
			System.out.println("NR");
		}
	}
	public TypeSolver getTypeSolver() {
		return typeSolver;
	}
	public void setTypeSolver(TypeSolver typeSolver) {
		this.typeSolver = typeSolver;
	}
}