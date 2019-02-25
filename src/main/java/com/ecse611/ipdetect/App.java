package com.ecse611.ipdetect;
import java.io.File;

import com.ecse611.ipdetect.RepoParser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.google.common.base.Strings;
import com.ecse611.ipdetect.DirExplorer;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class App 
{
	public static void main( String[] args ) throws Exception
	{	
		RepoParser rp = new RepoParser();
		rp.setProject_name("accumulo");
		rp.setRepo_path("/Users/svysali/Desktop/ecse611/assignment/repos/");
		//rp.printLogToCSV();
		File projectDir = new File("/Users/svysali/Desktop/ecse611/assignment/repos/accumulo");
        //listClasses(projectDir);
		listMethodCalls(projectDir);
	}
	
	private static void listMethodCalls(File projectDir) {
        new DirExplorer((level, path, file) -> path.endsWith(".java"), (level, path, file) -> {
            System.out.println(path);
            System.out.println(Strings.repeat("=", path.length()));
            try {
                new VoidVisitorAdapter<Object>() {
                    @Override
                    public void visit(MethodCallExpr n, Object arg) {
                        super.visit(n, arg);
                        System.out.println(" [L " + n.getBegin().get().line + "] " + n);
                    }
                }.visit(JavaParser.parse(file), null);
                System.out.println(); // empty line
            } catch (IOException e) {
                new RuntimeException(e);
            }
        }).explore(projectDir);
    }
	
	public static void listClasses(File projectDir) {
        new DirExplorer((level, path, file) -> path.endsWith(".java"), (level, path, file) -> {
            System.out.println(path);
            System.out.println(Strings.repeat("=", path.length()));
            try {
                new VoidVisitorAdapter<Object>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration n, Object arg) {
                        super.visit(n, arg);
                        System.out.println(" * " + n.getName());
                    }
                }.visit(JavaParser.parse(file), null);
                System.out.println(); // empty line
            } catch (IOException e) {
                new RuntimeException(e);
            }
        }).explore(projectDir);
    }
}

