package com.ecse611.ipdetect;

import org.apache.log4j.Logger;
import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

import com.ecse611.ipdetect.IPDetector;

public class SpoonParserVisitor implements CommitVisitor {
	private static final Logger logger = Logger.getLogger(SpoonParserVisitor.class);
	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
		try {
			
			IPDetector.generateJsonForCommit(repo.getScm(),commit.getHash());
		} catch(Exception E) {
			logger.error("Commit " + commit.getHash() + " " +E.getMessage());
			E.getStackTrace();
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


