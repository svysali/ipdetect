package com.ecse611.ipdetect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.repodriller.RepoDriller;
import org.repodriller.RepositoryMining;
import org.repodriller.Study;
import org.repodriller.domain.Commit;
import org.repodriller.filter.commit.OnlyNoMerge;
import org.repodriller.filter.range.Commits;
import org.repodriller.persistence.PersistenceMechanism;
import org.repodriller.persistence.csv.CSVFile;
import org.repodriller.scm.CommitVisitor;
import org.repodriller.scm.GitRepository;
import org.repodriller.scm.SCMRepository;
import com.ecse611.ipdetect.Config;

/*
 * Parses repository and writes commit-id,work-item-id to a csv
 * */
public class LogParser implements Study {
	public static void main( String[] args ) throws Exception
	{	
		new RepoDriller().start(new LogParser());
	}

	public void execute() {
		try {
			new RepositoryMining()
			.in(GitRepository.singleProject(Config.REPO_ROOT+Config.PROJECT))
			.through(Commits.all())
			.filters(
					new OnlyNoMerge()
				)
			.reverseOrder()
			.process(new LogMessageVisitor(Config.PROJECT),new CSVFile(Config.PROJECT+".csv"))
			.mine();
		}catch(Exception e){
			System.out.println("ERROR: " + e.getMessage());
		}
	}
}


class LogMessageVisitor implements CommitVisitor {

	private String project;
	public LogMessageVisitor(String p) {
		this.project = p;
	}
	@Override
	public void process(SCMRepository repo, Commit commit, PersistenceMechanism writer) {
		Pattern pattern = Pattern.compile(this.project+".?(\\d+)",Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(commit.getMsg());
		String work_id = "";
		if (matcher.find()) {
		    work_id = matcher.group(1);
		}
		writer.write(
			commit.getHash(),
			commit.getDate().toInstant().getEpochSecond(),
			work_id
		);
	}

	@Override
	public String name() {
		return "Log messages";
	}

}


