package com.ecse611.ipdetect;
import java.io.File;

import org.repodriller.RepoDriller;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.GitRepository;

import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.ecse611.ipdetect.JavaParserVisitor;

public class App implements Study 
{	
	public static void main( String[] args ) throws Exception
	{	
		new RepoDriller().start(new App());
	}

	public void execute() {
		TypeSolver typeSolver = new CombinedTypeSolver(
				new ReflectionTypeSolver(),
				new JavaParserTypeSolver(new File("/Users/svysali/Desktop/ecse611/assignment/repos/PDS/src")));
		try {
			new RepositoryMining()
			.in(GitRepository.singleProject("/Users/svysali/Desktop/ecse611/assignment/repos/PDS"))
			.through(Commits.single("b775820e26d20e0cb720c4045561f25b0a0c7ef7"))
			.process(new SpoonParserVisitor(),new CSVFile("devs.csv"))
			.mine();
		}catch(Exception e){
			System.out.println("ERRORRRRRR!!!!!!");
		}
		/* replace through with : 			
		 * .withCommits(
					new OnlyModificationsWithFileTypes(Arrays.asList(".java"),
					new OnlyNoMerge())
				) 
		 */
	}
}

