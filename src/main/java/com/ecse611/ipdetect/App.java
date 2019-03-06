package com.ecse611.ipdetect;

import org.repodriller.RepoDriller;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.filter.commit.OnlyNoMerge;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.CollectConfiguration;
import org.repodriller.scm.GitRepository;
import com.ecse611.ipdetect.Config;
/**
 * Parses source tree and writes as Json.  
 */

public class App implements Study 
{	
		
	public static void main( String[] args ) throws Exception
	{	
		new RepoDriller().start(new App());
	}

	public void execute() {		
		try {
			new RepositoryMining()
			.in(GitRepository.singleProject(Config.REPO_ROOT + Config.PROJECT))
			//.through(Commits.all())
			.through(Commits.range("9e05905ecafb839438b5b2bf49821a68b663ad4d","835d3dc24291ccb692cd10fe58510e291839d6f8"))
			.filters(
					new OnlyNoMerge()
				)
			.collect(new CollectConfiguration().basicOnly())
			.reverseOrder()
			.visitorsAreThreadSafe(true) // Threads are possible.
			.visitorsChangeRepoState(true) // Each thread needs its own copy of the repo.
			.withThreads(2)
			.process(new SpoonParserVisitor(),new CSVFile("devs.csv"))
			.mine();
		}catch(Exception e){
			System.out.println("ERROR in mining repository");
		}
	}
}

