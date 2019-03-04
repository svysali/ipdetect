# Project Title

ECSE 611 IP Commit Detector

## Getting Started

JDK 1.8, Eclipse ( With M2Eclipse )

### Prerequisites

Clone this repo and Import into Eclipse as a Maven project. Right Click -> Maven-> update project. This should download dependencies specified in the pom.xml.

###
Files Description
###
There are 3 files with Main functions in them :
1. IPDetector.java - This is the end to end workflow file. It reads the project.csv and runs the algorithm through each commit pair to give the results.
2. App.java - This is only to iterate through a repository to generate json files for each commit in the repository.
3. LogParser.java - This file creates the csv to be used by IPDetector.java. You wouldn't have to run this as the csv file for this study has already been checked in to the repo.

There are two other files for configuration
1.Config.java - To set repo root and project. Use this file to point to a local clone of your repository.
2.Weight.java - To configure the threshold and edge weights for the algorithm.

