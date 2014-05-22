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
	test {
		client {
			harvesterId = harvesterId // the unique harvester name, can be dynamic or static. Console only clients likely won't need this to be dynamic.
			description = "ReDBox OAI-PMH Harvester"
			base = "${managerBase}${harvesterId}/".toString() // optional base directory.
			autoStart = true // whether the Harvester Manager will start this harvester upon start up otherwise, it will be manually started by an administrator
			siPath = "src/main/resources/deploy-manager/applicationContext-SI-harvester.xml" // the app context definition for SI			
			classPathEntries = [] // entries that will be added to the class path
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
				user = "proai"
				pw = "proai"
				driver = "org.apache.derby.jdbc.EmbeddedDriver"
				url = "jdbc:derby:target/test/db/;create=true"				
			}
			sql {
				// the init and select entries below are purely for testing purposes, the tables will have to be initialized externally.				
				metadata {
					init = "CREATE TABLE provider_metadataformat (id INT not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), metadataPrefix varchar(1024), schemaTxt varchar(1024), metadataNamespace varchar(1024))"
					insert = "INSERT INTO provider_metadataformat (metadataPrefix, schemaTxt, metadataNamespace) VALUES (:metadataPrefix, :schemaTxt, :metadataNamespace)"
					select = "SELECT * FROM provider_metadataformat WHERE metadataPrefix=?"
				}
				record {
					init = "CREATE TABLE provider_records (id INT not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), metadataPrefix varchar(1024), recordId varchar(1024), source VARCHAR(32000), xmlEntry VARCHAR(32000))"
					insert = "INSERT INTO provider_records (metadataPrefix, source, recordId, xmlEntry) VALUES (:metadataPrefix,:source,:recordId,:xmlEntry)"
					select = "SELECT * FROM provider_records WHERE recordId=:recordId"
					delete = "DELETE FROM provider_records WHERE recordId=:recordId AND metadataPrefix=:metadataPrefix"
					update = "UPDATE provider_records SET xmlEntry=:xmlEntry, source=:source WHERE recordId=:recordId AND metadataPrefix=:metadataPrefix"
					nullupdate = "UPDATE provider_records SET xmlEntry='' WHERE recordId='NULL'"
				} 
				identify {
					init = "CREATE TABLE provider_identity (id INT not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), xmlEntry VARCHAR(32000))"
					insert = "INSERT INTO provider_identity (xmlEntry) VALUES (:xmlEntry)"
					select = "SELECT * FROM provider_identity"
				}
				set {
					init = "CREATE TABLE provider_sets (id INT not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1), spec varchar(1024), xmlEntry VARCHAR(32000))"
					insert = "INSERT INTO provider_sets (spec, xmlEntry) VALUES (:spec, :xmlEntry)"
					select = "SELECT * FROM provider_sets"
				}				
			}					
		}
		velocityTransformer {
			templateDir = "src/main/resources/deploy-manager/templates/"
			scriptDir = "src/main/resources/deploy-manager/scripts/"
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
			record_people_eac_cpf {
				templates = ["people/eac-cpf.vm", "people/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
			record_people_rif {
				templates = ["people/rif.vm", "people/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
			record_group_oai_dc {
				templates = ["group/oai_dc.vm", "group/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
			record_group_rif {
				templates = ["group/rif.vm", "group/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
			record_service_rif {
				templates = ["service/rif.vm", "service/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
			record_dataset_oai_dc {
				templates = ["dataset/oai_dc.vm", "dataset/record_wrapper.vm"]
				scripts {
					preVelocity = [["velocityTransformer-legacyUtil.groovy":""]]
				}
			}
			record_dataset_rif {
				templates = ["dataset/rif.vm", "dataset/record_wrapper.vm"]
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
			url = "vm://localhost?broker.persistent=false"
			dataDir = "target/activemq-data/"
		}
	}
}