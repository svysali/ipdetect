package com.ecse611.ipdetect;
import com.ecse611.ipdetect.RepoParser;
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
		rp.printLogToCSV();
	}
}
