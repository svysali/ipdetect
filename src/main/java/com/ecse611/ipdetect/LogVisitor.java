package com.ecse611.ipdetect;
import org.repodriller.domain.Commit;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.SCMRepository;

public class LogVisitor implements CommitVisitor {

	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
		
		writer.write(
			commit.getHash(),
			commit.getMsg()
		);

	}

	@Override
	public String name() {
		return "Log messages";
	}

}
