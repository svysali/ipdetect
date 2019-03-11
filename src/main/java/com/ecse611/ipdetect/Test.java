package com.ecse611.ipdetect;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class Test {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub
		getWorkIdSplit("accumulo_results/accumulo_cumulated.csv");
	}

	public static void getWorkIdSplit(String inputcsv) throws FileNotFoundException, IOException {
		HashBasedTable<String, String, Integer> work_id_table = HashBasedTable.create();
		BufferedReader br = new BufferedReader(new FileReader(inputcsv));
			String line;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				if(!work_id_table.containsRow(values[0])) {
					if(values[4].equalsIgnoreCase("true")) {
						work_id_table.put(values[0],"ip",1);
						work_id_table.put(values[0],"not_ip",0);
					} else {
						work_id_table.put(values[0],"ip",0);
						work_id_table.put(values[0],"not_ip",1);
					}
				} else {
					if(values[4].equalsIgnoreCase("true")) {
						work_id_table.put(values[0],"ip",(work_id_table.get(values[0], "ip").intValue()+1));
					} else {
						work_id_table.put(values[0],"not_ip",(work_id_table.get(values[0], "not_ip").intValue()+1));
					}
				}
			}
		double accuracy_total = 0.0; 
		for(String work_id:work_id_table.rowKeySet()) {
			int ip = work_id_table.get(work_id, "ip");
			int not_ip = work_id_table.get(work_id, "not_ip"); 
			double accuracy = (double)ip/(ip+not_ip);
			System.out.println(
					work_id+ "," +
					ip + "," +
					not_ip + "," +
					accuracy
			);
			accuracy_total+=accuracy;
		}
		System.out.println("Average_accuracy : " + accuracy_total/work_id_table.rowKeySet().size() );
	}
	
	
	
}
