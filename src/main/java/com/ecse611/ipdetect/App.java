package com.ecse611.ipdetect;

import java.util.Arrays;

import org.repodriller.RepoDriller;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.filter.commit.OnlyModificationsWithFileTypes;
import org.repodriller.filter.commit.OnlyNoMerge;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.GitRepository;

public class App implements Study 
{	
	public static void main( String[] args ) throws Exception
	{	
		new RepoDriller().start(new App());
	}

	public void execute() {
		/*try {
			new RepositoryMining()
			.in(GitRepository.singleProject("/Users/svysali/Desktop/ecse611/assignment/repos/PDS"))
			.through(Commits.single("28a67681650cb71b5e7c324e1fbfb258784f895a"))
			.process(new SpoonParserVisitor(),new CSVFile("devs.csv"))
			.mine();
		}catch(Exception e){
			System.out.println("ERRORRRRRR!!!!!!");
		}*/
		
		
		try {
			new RepositoryMining()
			.in(GitRepository.singleProject("/Users/svysali/Desktop/ecse611/assignment/repos/accumulo"))
			.through(Commits.all())
			.filters(
					new OnlyNoMerge(),
					new OnlyModificationsWithFileTypes(Arrays.asList(".java"))
				)
			.visitorsAreThreadSafe(true) // Threads are possible.
			.visitorsChangeRepoState(true) // Each thread needs its own copy of the repo.
			.withThreads()
			.process(new SpoonParserVisitor(),new CSVFile("devs.csv"))
			.mine();
		}catch(Exception e){
			System.out.println("ERRORRRRRR!!!!!!");
		}
	}
}

