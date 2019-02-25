package com.ecse611.ipdetect;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

public class RepoParser {
	private String project_name;
	private String repo_path;
	public String getProject_name() {
		return project_name;
	}
	public void setProject_name(String project_name) {
		this.project_name = project_name;
	}
	public String getRepo_path() {
		return repo_path;
	}
	public void setRepo_path(String repo_path) {
		this.repo_path = repo_path;
	}

	public void printLogToCSV() throws GitAPIException, IOException {
		String csvPath = this.project_name + ".csv";
		Git git = Git.open(new File(this.repo_path + this.project_name));
		Repository repo = git.getRepository();
		// work_item number pattern
		
		String pattern = this.project_name + ".?(\\d+)";
		
		// Create a Pattern object
		System.out.println(pattern);
		Pattern r = Pattern.compile(pattern,Pattern.CASE_INSENSITIVE);
		
		try(RevWalk walk = new RevWalk(repo)) {
			//start revwalk from HEAD commit
			walk.markStart(walk.parseCommit(repo.resolve("HEAD")));

			//Filter merge commits
			walk.setRevFilter(RevFilter.NO_MERGES);
			Iterator<RevCommit> i = walk.iterator();
			while(i.hasNext()){
				RevCommit commit = i.next();
				String commit_id = commit.getId().name();
				String commit_msg = commit.getShortMessage();
				int commit_time = commit.getCommitTime();
				Matcher m = r.matcher(commit_msg);
				String work_item_id = "NA";
				if (m.find()) {
					work_item_id =  m.group(0).toLowerCase();
				}
				System.out.println(commit_id+","+commit_time+","+work_item_id);
			}
		}
	}

	public static void getAllTags(Git git) throws GitAPIException, MissingObjectException, IncorrectObjectTypeException {
		List<Ref> call = git.tagList().call();
		for (Ref ref : call) {
			System.out.println("Tag: " + ref + " " + ref.getName() + " " + ref.getObjectId().getName());

			// fetch all commits for this tag
			LogCommand log = git.log();

			Ref peeledRef = git.getRepository().peel(ref);
			if(peeledRef.getPeeledObjectId() != null) {
				// Annotated tag
				log.add(peeledRef.getPeeledObjectId());
			} else {
				// Lightweight tag
				log.add(ref.getObjectId());
			}
			System.out.println(log.toString());
		}

	}

}
