/*******************************************************************************
 *Copyright (C) 2014 Queensland Cyber Infrastructure Foundation (http://www.qcif.edu.au/)
 *
 *This program is free software: you can redistribute it and/or modify
 *it under the terms of the GNU General Public License as published by
 *the Free Software Foundation; either version 2 of the License, or
 *(at your option) any later version.
 *
 *This program is distributed in the hope that it will be useful,
 *but WITHOUT ANY WARRANTY; without even the implied warranty of
 *MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *GNU General Public License for more details.
 *
 *You should have received a copy of the GNU General Public License along
 *with this program; if not, write to the Free Software Foundation, Inc.,
 *51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 ******************************************************************************/
/**
 * ReDBox OAI-PMH Feed Harvester Client configuration
 * 
 * @author <a href="https://github.com/shilob">Shilo Banihit</a>
 *
 */
// Environment specific config below...
environments {
	production {
		client {
			harvesterId = harvesterId // the unique harvester name, can be dynamic or static. Console only clients likely won't need this to be dynamic.
			description = "ReDBox OAI-PMH Harvester"
			base = "${managerBase}${harvesterId}/".toString() // optional base directory. 
			autoStart = true // whether the Harvester Manager will start this harvester upon start up otherwise, it will be manually started by an administrator 
			siFile = "applicationContext-SI-harvester.xml" // the app context definition for SI
			siPath = base+siFile // the path used when starting this harvester
			classPathEntries = ["resources/lib/postgresql-9.3-1101-jdbc41.jar","resources/lib/xbean-spring-3.16.jar"] // entries that will be added to the class path
			mbeanExporter = "mbeanExporterOaiFeed" // the exporter is necessary for orderly shutdown
			orderlyShutdownTimeout = 10000 // in ms 
		}
		file {
			runtimePath = client.base+"runtime/" + configPath
			customPath = client.base+"custom/" + configPath
			ignoreEntriesOnSave = ["runtime"]
		}
		harvest {			
			jdbc {
				user = "oaiserver"
				pw = "oaiserver"
				driver = "org.postgresql.Driver"
				url = "jdbc:postgresql://localhost/oaiserver"
			}
			sql {				
				metadata {
					insert = "INSERT INTO provider_metadataformat (metadataPrefix, schemaTxt, metadataNamespace) VALUES (:metadataPrefix, :schemaTxt, :metadataNamespace);"
				}
				record {
					insert = "INSERT INTO provider_records (metadataPrefix, source, recordId, xmlEntry)  VALUES (:metadataPrefix, :source, :recordId, :xmlEntry)"
					select = "SELECT * FROM provider_records WHERE recordId=:recordId"
					delete = "DELETE FROM provider_records WHERE recordId=:recordId AND metadataPrefix=:metadataPrefix"
					update = "UPDATE provider_records SET xmlEntry=:xmlEntry, source=:source WHERE recordId=:recordId AND metadataPrefix=:metadataPrefix"
					nullupdate = "UPDATE provider_records SET xmlEntry='' WHERE recordId='NULL'"
				} 
				identify {
					insert = "INSERT INTO provider_identity (xmlEntry) VALUES (:xmlEntry)"
				}
				set {
					insert = "INSERT INTO provider_sets (spec, xmlEntry) VALUES (:spec, :xmlEntry)"
				}				
			}					
		}
		velocityTransformer {
			templateDir = client.base+"templates/"
			scriptDir = client.base+"scripts/"
			metadataFormat {
				templates = []
			}
			record_person_eac_cpf {
				templates = ["person/eac-cpf.vm"]
				scripts {
					preVelocity = [["velocityTransformer-stringUtils.groovy":""]]
				}
			}
			record_person_oai_dc {
				templates = ["person/oai_dc.vm"]
				scripts {
					preVelocity = [["velocityTransformer-stringUtils.groovy":""]]
				}
			}
			record_people_oai_dc {
				templates = ["people/oai_dc.vm", "people/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
		}
		cloningSplitter {
			srcArray = "metadataPrefix"
			modifyHeader = "type"
			entryHeader = "metadataPrefix"
		}
		activemq {
			url = "tcp://0.0.0.0:9301"
			dataDir = client.base + "activemq-data/"
		}
	}	
}