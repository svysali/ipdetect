package com.ecse611.ipdetect;

import org.repodriller.RepoDriller;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
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
		try {
			new RepositoryMining()
			.in(GitRepository.singleProject("/Users/svysali/Desktop/ecse611/assignment/repos/PDS"))
			.through(Commits.single("b775820e26d20e0cb720c4045561f25b0a0c7ef7"))
			.process(new SpoonParserVisitor(),new CSVFile("devs.csv"))
			.mine();
		}catch(Exception e){
			System.out.println("ERRORRRRRR!!!!!!");
		}
	}
}

