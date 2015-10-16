#
# This file is part of the PSL software.
# Copyright 2011-2013 University of Maryland
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

#
# This script runs PSL. Its only dependency is Maven 3.
#

export PSL_VERSION=1.3-SNAPSHOT

if [ ! -f "pom.xml" ]
then 
	echo -e "\
<?xml version=\"1.0\" encoding=\"UTF-8\"?> \n\
<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n\
	xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\"> \n\
	<modelVersion>4.0.0</modelVersion> \n\
	<groupId>edu.umd.cs</groupId> \n\
	<artifactId>psl-cli-stub</artifactId> \n\
	<name>psl-cli-stub</name> \n\
	<version>$PSL_VERSION</version> \n\
	<packaging>jar</packaging> \n\
	<description>A stub POM file for running PSL from the command line.</description> \n\
	<dependencies> \n\
		<dependency> \n\
			<groupId>edu.umd.cs</groupId> \n\
			<artifactId>psl-cli</artifactId> \n\
			<version>$PSL_VERSION</version> \n\
		</dependency> \n\
	</dependencies> \n\
	<repositories> \n\
		<repository> \n\
			<releases> \n\
				<enabled>true</enabled> \n\
				<updatePolicy>daily</updatePolicy> \n\
				<checksumPolicy>fail</checksumPolicy> \n\
			</releases> \n\
			<id>psl-releases</id> \n\
			<name>PSL Releases</name> \n\
			<url>https://scm.umiacs.umd.edu/maven/lccd/content/repositories/psl-releases/</url> \n\
			<layout>default</layout> \n\
		</repository> \n\
		<repository> \n\
			<releases> \n\
				<enabled>true</enabled> \n\
				<updatePolicy>daily</updatePolicy> \n\
				<checksumPolicy>fail</checksumPolicy> \n\
			</releases> \n\
			<id>psl-thirdparty</id> \n\
			<name>PSL Third Party</name> \n\
			<url>https://scm.umiacs.umd.edu/maven/lccd/content/repositories/psl-thirdparty/</url> \n\
			<layout>default</layout> \n\
		</repository> \n\
	</repositories> \n\
</project>" > pom.xml
fi

if [ ! -f "classpath.out" ]
then
	echo "One minute please. PSL is building its classpath."
	mvn dependency:build-classpath -Dmdep.outputFile=classpath.out > /dev/null
fi

java -cp `cat classpath.out` edu.umd.cs.psl.cli.Launcher "$@"