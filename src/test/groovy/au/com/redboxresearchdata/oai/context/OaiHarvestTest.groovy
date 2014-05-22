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
package au.com.redboxresearchdata.oai.context

import groovy.json.JsonBuilder
import groovy.sql.Sql
import javax.sql.DataSource
import org.apache.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.context.support.FileSystemXmlApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.integration.MessageChannel
import org.springframework.integration.endpoint.AbstractEndpoint
import org.springframework.integration.support.MessageBuilder
import org.springframework.jmx.support.MBeanServerFactoryBean

/**
 * Tests the OAI-PMH Feed to the DB.
 * 
 * @author <a href="https://github.com/shilob">Shilo Banihit</a>
 *
 */
class OaiHarvestTest extends GroovyTestCase {
	private static final Logger logger = Logger.getLogger(OaiHarvestTest.class)
	
	def grailsApplication = [:]
	def sql
	def appContext
	def config
	def grailsConfig
	MessageChannel oaiHarvestMainChannel
	
	void setUp() {
		new File ("target/test/db/").deleteDir()
		config = new ConfigSlurper("test").parse(new File("src/test/resources/harvester-config-test.groovy").toURI().toURL())
		grailsConfig = ["environment":"test", "clientConfigObj":config]
		grailsApplication.config = grailsConfig
		
		MBeanServerFactoryBean mbeanServer = new MBeanServerFactoryBean()
		DefaultListableBeanFactory parentBeanFactory = new DefaultListableBeanFactory()
		parentBeanFactory.registerSingleton("grailsApplication", grailsApplication)
		parentBeanFactory.registerSingleton("mbeanServer", mbeanServer)
		GenericApplicationContext parentContext = new GenericApplicationContext(parentBeanFactory)
		parentContext.refresh()
		
		logger.info("Starting SI...")
		String[] locs = ["file:"+config.client.siPath]
		appContext = new FileSystemXmlApplicationContext(locs, true, parentContext)
		appContext.registerShutdownHook()
		sql = new Sql((DataSource)appContext.getBean("dataSource"))
		
		oaiHarvestMainChannel = appContext.getBean("oaiHarvestMainChannel", MessageChannel.class)
	}
	// ---------------------------------------------------------------------------------------------------
	void tearDown() {
		logger.info("Test completed! Shutting down SI...")
		def mbeanExporter = appContext.getBean("mbeanExporterOaiFeed")
		mbeanExporter.stopActiveComponents(false, 5000)
		logger.info("Shutdown command success.")
	}
	// ----- The main test method ----
	void testHarvest() {
		doMetadataFormat()
		doIdentity()
		doSet()
		doRecordPerson()
		doRecordPeople()
		doRecordGroup()
		doRecordService()
	}
	// ------ The Tests -------------
	// ---------------------------------------------------------------------------------------------------
	void doMetadataFormat() {
		logger.info("Testing Metadataformat")
		logger.info("Creating DB table: ${config.harvest.sql.metadata.init}")
		sql.execute(config.harvest.sql.metadata.init)
		def metadataPrefix = "eac-cpf"
		def oai_dc = "oai_dc"
		def schemaTxt = "urn:isbn:1-931666-33-4 http://eac.staatsbibliothek-berlin.de/schema/cpf.xsd"
		def metadataNamespace = "urn:isbn:1-931666-33-4"
		def request = """
		{
			"header": {
				"type":"metadataFormat"
			},
			"data":[
				{
					"metadataPrefix":"${metadataPrefix}",
					"schemaTxt":"${schemaTxt}",
					"metadataNamespace":"${metadataNamespace}"
				},
				{
					"metadataPrefix":"${oai_dc}",
					"schemaTxt":"${schemaTxt}",
					"metadataNamespace":"${metadataNamespace}"
				}
			]
		}
		"""
		logger.info("Sending test metadataformat message....")
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating metadataFormat....")
		def row = sql.firstRow(config.harvest.sql.metadata.select, [metadataPrefix])
		assertNotNull(row)
		assertEquals(row.metadataPrefix, metadataPrefix)
		assertEquals(row.schemaTxt, schemaTxt)
		assertEquals(row.metadataNamespace, metadataNamespace)
		row = sql.firstRow(config.harvest.sql.metadata.select, [oai_dc])
		assertNotNull(row)
		assertEquals(row.metadataPrefix, oai_dc)
		assertEquals(row.schemaTxt, schemaTxt)
		assertEquals(row.metadataNamespace, metadataNamespace)
		logger.info("Metadataformat passed.")
	}
	// ---------------------------------------------------------------------------------------------------
	void doIdentity() {
		logger.info("Testing Identity")
		logger.info("Creating DB table:${config.harvest.sql.identify.init}")
		sql.execute(config.harvest.sql.identify.init)
		def xmlEntry = "<Identify>    <repositoryName>The Fascinator</repositoryName>    <baseURL>http://demo.redboxresearchdata.com.au/mint/default</baseURL>    <protocolVersion>2.0</protocolVersion>    <adminEmail>fascinator@usq.edu.au</adminEmail>      <earliestDatestamp>0001-01-01T00:00:00Z</earliestDatestamp>    <deletedRecord>persistent</deletedRecord>    <granularity>YYYY-MM-DDThh:mm:ssZ</granularity>    <description>        <oai-identifier xsi:schemaLocation='http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd' xmlns='http://www.openarchives.org/OAI/2.0/oai-identifier'>            <scheme>oai</scheme>            <repositoryIdentifier>fascinator.usq.edu.au</repositoryIdentifier>            <delimiter>:</delimiter>            <sampleIdentifier>oai:fascinator.usq.edu.au:5e8ff9bf55ba3508199d22e984129be6</sampleIdentifier>        </oai-identifier>    </description></Identify> "
		def request = """
		{
			"header": {
				"type":"identify"
			},
			"data":[
				{
					"xmlEntry":"${xmlEntry}"
				}
			]
		}
		"""		
		logger.info("Sending Identity message....")
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Identity....")
		def row = sql.firstRow(config.harvest.sql.identify.select)
		assertNotNull(row)
		assertEquals(xmlEntry.toString(), row.xmlEntry)
		logger.info("Identity passed.")
	}
	// ---------------------------------------------------------------------------------------------------
	void doSet() {
		logger.info("Testing Sets")
		logger.info("Creating DB table:${config.harvest.sql.set.init}")
		sql.execute(config.harvest.sql.set.init)
		def spec = "Parties_People"
		def xmlEntry = "<set><setSpec>Parties_People</setSpec><setName>Parties - People</setName></set>"
		def request = """
		{
			"header": {
				"type":"set"
			},
			"data":[
				{
					"spec":"${spec}",
					"xmlEntry":"${xmlEntry}"
				}
			]
		}
		"""	
		logger.info("Sending Set message....")
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Set....")
		def row = sql.firstRow(config.harvest.sql.set.select)
		assertNotNull(row)
		assertEquals(xmlEntry.toString(), row.xmlEntry)
		assertEquals(spec, row.spec)
	}
	// ---------------------------------------------------------------------------------------------------
	void doRecordPerson() {
		logger.info("Testing Record Person...")
		logger.info("Testing CurationManager data feed.....Person")
		logger.info("Creating DB table:${config.harvest.sql.record.init}")
		sql.execute(config.harvest.sql.record.init)
		def recordSource = "Unit-Test: Any arbitrary string that identifies the source of this publish request."
		def recordId = "record-1: a unique DB identifier, could be OID"
		def mdPrefix = ["eac-cpf", "oai_dc"]
		def jsonMapData = [
			"header":[
				"type":"record_person"
			],
			"data":[
				[
					"recordId":recordId,
					"metadataPrefix":mdPrefix,
					"source":recordSource,
					"jsonData":[
						"recordId":"d082b0890570265c99b52f360a674112",
						"control":[
							"maintenanceAgency":["agencyCode":"TO-DO", "agencyName":"The University of Examples, Australia"],
							"maintenanceHistory":[
									"maintenanceEvent":[
										"eventDateTime_standardDateTime":"",
										"agent":"Mint Name Authority - The University of Examples, Australia"
									]
							]
						],
						"entityId":"http://demo.redboxresearchdata.com.au/mint/published/detail/d082b0890570265c99b52f360a674112",
						"surname":"Zweinstein",
						"forename":"Alberto",
						"salutation":"Mr",
						"description":"Dr Alberto Zweinstein is a Lecturer at the University of Examples",
						"dateStamp":"2014-03-18T06:09:03Z"
					]
				]
			]
		]
		def request = new JsonBuilder(jsonMapData).toString()
		logger.info("Sending Record message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record....")
		def rows = sql.rows([recordId:recordId],config.harvest.sql.record.select)
		rows.each {rowEntry->
			assertNotNull(rowEntry)
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
			assertTrue("eac-cpf" == rowEntry.metadataPrefix || "oai_dc" == rowEntry.metadataPrefix)
			assertNotNull(rowEntry.xmlEntry)
			def parsedXml = new XmlSlurper().parseText(rowEntry.xmlEntry)
			// validating header..
			assertEquals(jsonMapData.data[0].jsonData.entityId, parsedXml.header.identifier.toString())
			assertEquals(jsonMapData.data[0].jsonData.dateStamp, parsedXml.header.datestamp.toString())
			assertEquals("Parties_People", parsedXml.header.setSpec.toString())
			if ("eac-cpf" == rowEntry.metadataPrefix) {
				def eacCpf = parsedXml.metadata["eac-cpf"]
				assertEquals(jsonMapData.data[0].jsonData.recordId, eacCpf.control.recordId.toString())
				assertEquals(jsonMapData.data[0].jsonData.control.maintenanceAgency.agencyCode, eacCpf.control.maintenanceAgency.agencyCode.toString())
				assertEquals(jsonMapData.data[0].jsonData.control.maintenanceAgency.agencyName, eacCpf.control.maintenanceAgency.agencyName.toString())
				assertEquals(jsonMapData.data[0].jsonData.control.maintenanceHistory.maintenanceEvent.eventDateTime_standardDateTime, eacCpf.control.maintenanceHistory.maintenanceEvent.eventDateTime.@standardDateTime.toString())
				assertEquals(jsonMapData.data[0].jsonData.control.maintenanceHistory.maintenanceEvent.agent, eacCpf.control.maintenanceHistory.maintenanceEvent.agent.toString())
				assertEquals(jsonMapData.data[0].jsonData.entityId, eacCpf.cpfDescription.identity.entityId.toString())
				assertEquals(jsonMapData.data[0].jsonData.surname, eacCpf.cpfDescription.identity.nameEntry.part.findAll{it.@localType =~ "surname"}[0].toString())
				assertEquals(jsonMapData.data[0].jsonData.forename, eacCpf.cpfDescription.identity.nameEntry.part.findAll{it.@localType =~ "forename"}[0].toString())
				assertEquals(jsonMapData.data[0].jsonData.description, eacCpf.cpfDescription.description.biogHist.abstract.toString())
			}
			if ("oai_dc" == rowEntry.metadataPrefix) {
				def oaiDc = parsedXml.metadata["dc"]
				
				assertEquals("${jsonMapData.data[0].jsonData.salutation} ${jsonMapData.data[0].jsonData.forename} ${jsonMapData.data[0].jsonData.surname}", oaiDc["title"].toString())
				assertEquals(jsonMapData.data[0].jsonData.description, oaiDc["description"].toString())
			}
		}
	}
	// ---------------------------------------------------------------------------------------------------
	void doRecordPeople() {		 
		logger.info("-------------------------------------------------------------------------")
		logger.info("Testing for RB/Mint Data feed......People")
		logger.info("-------------------------------------------------------------------------")
		def recordId = "e6656a2c87d086b015db7e4d9e60c65e"
		def mdPrefix = ["oai_dc", "eac-cpf", "rif"]
		def recordSource = "Unit-Test: Any arbitrary string that identifies the source of this publish request."
		def jsonMapData = [
			"header":[ // control header for identifying this record
				"type":"record_people"
			],
			"data":[ // the actual payload
				[ // first entry of the payload
					"recordId":recordId,
					"dateStamp":"2014-03-18T06:09:03Z",
					"metadataPrefix":mdPrefix,
					"source":recordSource,
					"metadata": [ // the actual data from RB/Mint/CM
						"recordIDPrefix": "redbox-mint.googlecode.com/parties/people/",
					    "data": [
					        "ID": "1242",
					        "Given_Name": "Monique",
					        "Other_Names": "Louise",
					        "Family_Name": "Krupp",
					        "Pref_Name": "Monique",
					        "Honorific": "Mrs",
					        "Email": "monique.krupp@example.edu.au",
					        "Job_Title": "Lecturer",
					        "GroupID_1": "9",
					        "GroupID_2": "11",
					        "GroupID_3": "",
					        "ANZSRC_FOR_1": "1205",
					        "ANZSRC_FOR_2": "1299",
					        "ANZSRC_FOR_3": "1202",
					        "URI": "http://id.example.edu.au/people/1242",
					        "NLA_Party_Identifier": "http://nla.gov.au/nla.party-100009",
					        "ResearcherID": "http://www.researcherid.com/rid/F-1234-5686",
					        "openID": "https://plus.google.com/blahblahblah",
					        "Personal_URI": "",
					        "Personal_Homepage": "http://www.krupp.com",
					        "Staff_Profile_Homepage": "http://staff.example.edu.au/Monique.Krupp",
					        "Description": "Mrs Monique Krupp is a Lecturer at the University of Examples"
					    ],
					    "metadata": [
					        "dc.identifier": "redbox-mint.googlecode.com/parties/people/1242"
					    ],
					    "relationships": [
					        [
					            "identifier": "redbox-mint.googlecode.com/parties/group/9",
					            "relationship": "isMemberOf",
					            "reverseRelationship": "hasMember",
					            "authority": true,
					            "oid": "58ea8c1f2642f20e87efccabb55bf3d3",
					            "isCurated": true,
					            "curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/58ea8c1f2642f20e87efccabb55bf3d3"
					        ],
					        [
					            "identifier": "redbox-mint.googlecode.com/parties/group/11",
					            "relationship": "isMemberOf",
					            "reverseRelationship": "hasMember",
					            "authority": true,
					            "oid": "5350c453df62c9545679c54ab3966d51",
					            "isCurated": true,
					            "curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/5350c453df62c9545679c54ab3966d51"
					        ],
					        [
					            "identifier": "http://demo.redboxresearchdata.com.au/redbox/published/detail/03abc59403657a3978e58d8b27bd486e",
					            "curatedPid": "http://demo.redboxresearchdata.com.au/redbox/published/detail/03abc59403657a3978e58d8b27bd486e",
					            "broker": "tcp://localhost:9101",
					            "isCurated": true,
					            "relationship": "hasAssociationWith",
					            "uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/redbox\\/published\\/detail\\/03abc59403657a3978e58d8b27bd486e\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/redbox\\/published\\/detail\\/03abc59403657a3978e58d8b27bd486e\",\"broker\":\"tcp:\\/\\/localhost:9101\",\"isCurated\":true,\"relationship\":\"hasAssociationWith\"}"
					        ]
					    ],
					    "responses": [
					        [
					            "broker": "tcp://localhost:9101",
					            "oid": "03abc59403657a3978e58d8b27bd486e",
					            "task": "curation-pending",
					            "quoteId": "redbox-mint.googlecode.com/parties/people/1242"
					        ]
					    ]
					],
					"objectMetadata":[
						"render-pending":false,
						"repository.type":"Parties",
						"metaPid":"TF-OBJ-META",
						"jsonConfigOid":"d477decc3e982f2acfeed3d0c1ac3867",
						"ready_to_publish":"ready",
						"jsonConfigPid":"Parties_People.json",
						"repository.name":"People",
						"IngestedRelationshipsTransformer":"hasRun",
						"rulesOid":"f70c991f8b9731922430abb1e0115cb5",
						"published":true,
						"objectId":"e6656a2c87d086b015db7e4d9e60c65e",
						"scriptType":"python",
						"rulesPid":"Parties_People.py",
						"localPid":"http://demo.redboxresearchdata.com.au/mint/published/detail/e6656a2c87d086b015db7e4d9e60c65e",
						"eac_creation_date":"2014-03-18T06:09:03Z"
						
					],
					"constants": [
						"oai_dc": [
							"curation":["pidProperty":"localPid"] // using the local Pid as identifier
						],
						"eac-cpf": [
							"curation":[
								"pidProperty":"localPid",
								"nlaIntegration":[
									"agencyCode":"AgencyCode",
									"agencyName":"AgencyName"
								]
							],
							"redbox.identity": [
								"institution": "University of Examples",
								"RIF-CS Group": "The University of Examples, Australia"
							]
						],
						"rif": [
							"urlBase":"http://localhost:9001/mint/",
							"curation":["pidProperty":"localPid"],
							"redbox.identity": [
								"institution": "University of Examples",
								"RIF-CS Group": "The University of Examples, Australia"
							]
						]
					]
				]
			]
		]
		def request = new JsonBuilder(jsonMapData).toString()
		logger.info("Sending Record message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record....")
		def rows = sql.rows([recordId:recordId],config.harvest.sql.record.select)
		boolean hasEac = false
		boolean hasOai = false
		boolean hasRif = false
		rows.each {rowEntry->
			assertNotNull(rowEntry)
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
			if ("oai_dc" == rowEntry.metadataPrefix)
				hasOai = true
			if ("eac-cpf" == rowEntry.metadataPrefix)
				hasEac = true
			if ("rif" == rowEntry.metadataPrefix)
				hasRif = true
				
			assertTrue(hasOai || hasEac || hasRif)
			assertNotNull(rowEntry.xmlEntry)
			def parsedXml = new XmlSlurper().parseText(rowEntry.xmlEntry)
			// validating header..
			assertEquals(jsonMapData.data[0].recordId, parsedXml.header.identifier.toString())
			assertEquals(jsonMapData.data[0].dateStamp, parsedXml.header.datestamp.toString())
			assertEquals("Parties_People", parsedXml.header.setSpec.toString())
			if ("eac-cpf" == rowEntry.metadataPrefix) {
				def eacCpf = parsedXml.metadata["eac-cpf"]
				logger.info("Validating EAC CPF")
				// validate eac-cpf specific bits
				
				assertEquals(jsonMapData.data[0].recordId, eacCpf.control.recordId.toString())
				assertEquals(jsonMapData.data[0].constants["eac-cpf"].curation.nlaIntegration.agencyCode, eacCpf.control.maintenanceAgency.agencyCode.toString())
				assertEquals(jsonMapData.data[0].constants["eac-cpf"].curation.nlaIntegration.agencyName, eacCpf.control.maintenanceAgency.agencyName.toString())
				assertEquals(jsonMapData.data[0].metadata.data.Given_Name, eacCpf.cpfDescription.identity.nameEntry.part.findAll{it.@localType=="forename"}[0].toString())
				assertEquals(jsonMapData.data[0].metadata.data.Family_Name, eacCpf.cpfDescription.identity.nameEntry.part.findAll{it.@localType=="surname"}[0].toString())
				// TODO: add more fields to check
			}
			if ("oai_dc" == rowEntry.metadataPrefix) {
				logger.info("Validating OAI-DC")
				def oaiDc = parsedXml.metadata["dc"]
				
				assertEquals(jsonMapData.data[0].objectMetadata.localPid, oaiDc["identifier"].toString())
				String name = "${jsonMapData.data[0].metadata.data.Honorific} ${jsonMapData.data[0].metadata.data.Given_Name} ${jsonMapData.data[0].metadata.data.Family_Name}"
				assertEquals(name, oaiDc["title"].toString())
				assertEquals("'Parties' entry for '$name'", oaiDc["description"].toString())
			}
			if ("rif" == rowEntry.metadataPrefix) {
				logger.info("Validating RIF")
				def rif = parsedXml.metadata["registryObjects"]
				
				def primaryName = rif.registryObject.party.name.findAll{it.@type == "primary"}
				assertEquals(1, primaryName.size())
				
				assertEquals(jsonMapData.data[0].metadata.data.Given_Name, primaryName[0].namePart.findAll{it.@type=="given"}[0].toString())
				assertEquals(jsonMapData.data[0].metadata.data.Family_Name, primaryName[0].namePart.findAll{it.@type=="family"}[0].toString())
				// TODO: add more fields to check
			}
		}
		assertTrue(hasOai && hasEac && hasRif)
		logger.info("-------------------------------------------------------------------------")
		logger.info("Testing Record Update...")
		logger.info("-------------------------------------------------------------------------")
		jsonMapData.data[0].metadata.data.Given_Name = "Given Name"
		request = new JsonBuilder(jsonMapData).toString()
		logger.info("Sending Record Update message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record Update....")
		rows = sql.rows([recordId:recordId],config.harvest.sql.record.select)
		assertEquals(mdPrefix.size(), rows.size())
		rows.each {rowEntry->
			assertNotNull(rowEntry)
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
			assertTrue("eac-cpf" == rowEntry.metadataPrefix || "oai_dc" == rowEntry.metadataPrefix || "rif" == rowEntry.metadataPrefix)
			assertNotNull(rowEntry.xmlEntry)
			def parsedXml = new XmlSlurper().parseText(rowEntry.xmlEntry)
			// validating header..
			assertEquals(jsonMapData.data[0].recordId, parsedXml.header.identifier.toString())
			assertEquals(jsonMapData.data[0].dateStamp, parsedXml.header.datestamp.toString())
			assertEquals("Parties_People", parsedXml.header.setSpec.toString())
			if ("eac-cpf" == rowEntry.metadataPrefix) {
				def eacCpf = parsedXml.metadata["eac-cpf"]
				logger.info("Validating Updated EAC CPF ------------")
			}
			if ("oai_dc" == rowEntry.metadataPrefix) {
				logger.info("Validating Updated OAI-DC")
				def oaiDc = parsedXml.metadata["dc"]
				assertEquals(jsonMapData.data[0].objectMetadata.localPid, oaiDc["identifier"].toString())
				String name = "${jsonMapData.data[0].metadata.data.Honorific} ${jsonMapData.data[0].metadata.data.Given_Name} ${jsonMapData.data[0].metadata.data.Family_Name}"
				assertEquals(name, oaiDc["title"].toString())
				assertEquals("'Parties' entry for '$name'", oaiDc["description"].toString())
			}
		}
		
		logger.info("Testing Record People passed.")
	}
	// ---------------------------------------------------------------------------------------------------
	void doRecordGroup() {
		logger.info("-------------------------------------------------------------------------")
		logger.info("Testing for RB/Mint Data feed......Group")
		logger.info("-------------------------------------------------------------------------")
		def recordId = "d2848c47c708d6834b86a1cca9e4c2c1"
		def mdPrefix = ["rif", "oai_dc"]
		def recordSource = "Unit-Test: Any arbitrary string that identifies the source of this publish request."
		def jsonMapData = [
			"header":[ // control header for identifying this record
				"type":"record_group"
			],
			"data":[ // the actual payload
				[ // first entry of the payload
					"recordId":recordId,
					"dateStamp":"2014-03-18T06:09:03Z",
					"metadataPrefix":mdPrefix,
					"source":recordSource,
					"metadata": [ // the actual data from RB/Mint/CM
						"recordIDPrefix": "redbox-mint.googlecode.com/parties/group/",
						"data": [
							"ID": "1",
							"Name": "University of Examples",
							"Email": "info@example.edu.au",
							"Phone": "012 345 6789",
							"Parent_Group_ID": "",
							"URI": "http://id.example.edu.au",
							"NLA_Party_Identifier": "http://nla.gov.au/nla.party-000001",
							"Homepage": "http://www.example.edu.au",
							"Description": "A non-existent institution offering education and research of dubious use"
						],
						"metadata": [
							"dc.identifier": "redbox-mint.googlecode.com/parties/group/1"
						],
						"relationships": [
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/5322086dccb0f5db54d84a44476b9de9",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/5322086dccb0f5db54d84a44476b9de9",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "5322086dccb0f5db54d84a44476b9de9",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/5322086dccb0f5db54d84a44476b9de9\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/5322086dccb0f5db54d84a44476b9de9\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"5322086dccb0f5db54d84a44476b9de9\"}"
							],
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/4a032578267d4f68537228e4ed8422fe",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/4a032578267d4f68537228e4ed8422fe",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "4a032578267d4f68537228e4ed8422fe",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/4a032578267d4f68537228e4ed8422fe\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/4a032578267d4f68537228e4ed8422fe\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"4a032578267d4f68537228e4ed8422fe\"}"
							],
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/f8089bfbdedb57b3ade75f32d2dd4812",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/f8089bfbdedb57b3ade75f32d2dd4812",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "f8089bfbdedb57b3ade75f32d2dd4812",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/f8089bfbdedb57b3ade75f32d2dd4812\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/f8089bfbdedb57b3ade75f32d2dd4812\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"f8089bfbdedb57b3ade75f32d2dd4812\"}"
							],
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/2e8cbfb7a6e9a43efcf1051f3eb0f5f7",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/2e8cbfb7a6e9a43efcf1051f3eb0f5f7",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "2e8cbfb7a6e9a43efcf1051f3eb0f5f7",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/2e8cbfb7a6e9a43efcf1051f3eb0f5f7\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/2e8cbfb7a6e9a43efcf1051f3eb0f5f7\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"2e8cbfb7a6e9a43efcf1051f3eb0f5f7\"}"
							],
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/be69b9b1d2dbc0de2f1629bef3b300f2",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/be69b9b1d2dbc0de2f1629bef3b300f2",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "be69b9b1d2dbc0de2f1629bef3b300f2",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/be69b9b1d2dbc0de2f1629bef3b300f2\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/be69b9b1d2dbc0de2f1629bef3b300f2\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"be69b9b1d2dbc0de2f1629bef3b300f2\"}"
							],
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/aa602ace95f5a980087ac5ea6365032e",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/aa602ace95f5a980087ac5ea6365032e",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "aa602ace95f5a980087ac5ea6365032e",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/aa602ace95f5a980087ac5ea6365032e\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/aa602ace95f5a980087ac5ea6365032e\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"aa602ace95f5a980087ac5ea6365032e\"}"
							],
							[
								"identifier": "http://demo.redboxresearchdata.com.au/mint/published/detail/77b31218bb35b2aa8d9741ad04025678",
								"curatedPid": "http://demo.redboxresearchdata.com.au/mint/published/detail/77b31218bb35b2aa8d9741ad04025678",
								"broker": "tcp://localhost:9201",
								"isCurated": true,
								"relationship": "hasPart",
								"oid": "77b31218bb35b2aa8d9741ad04025678",
								"uniqueString": "{\"identifier\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/77b31218bb35b2aa8d9741ad04025678\",\"curatedPid\":\"http:\\/\\/demo.redboxresearchdata.com.au\\/mint\\/published\\/detail\\/77b31218bb35b2aa8d9741ad04025678\",\"broker\":\"tcp:\\/\\/localhost:9201\",\"isCurated\":true,\"relationship\":\"hasPart\",\"oid\":\"77b31218bb35b2aa8d9741ad04025678\"}"
							]
						]
					],
					"objectMetadata":[
						"render-pending":false,
						"repository.type":"Parties",
						"metaPid":"TF-OBJ-META",
						"jsonConfigOid":"62c73bcca2807e9966450bba20205d86",
						"ready_to_publish":"ready",
						"jsonConfigPid":"Parties_Groups.json",
						"repository.name":"Groups",
						"published":true,
						"rulesOid":"5cb8939d887282808ea2612c5bfc925d",
						"objectId":"d2848c47c708d6834b86a1cca9e4c2c1",
						"scriptType":"python",
						"rulesPid":"Parties_Groups.py",
						"localPid":"http://demo.redboxresearchdata.com.au/mint/published/detail/d2848c47c708d6834b86a1cca9e4c2c1"						
					],
					"constants": [
						"oai_dc": [
							"curation":["pidProperty":"localPid"] // using the local Pid as identifier
						],
						"rif": [
							"urlBase":"http://localhost:9001/mint/",
							"curation":["pidProperty":"localPid"],
							"redbox.identity": [
								"institution": "University of Examples",
								"RIF-CS Group": "The University of Examples, Australia"
							]
						]
					]
				]
			]
		]
		def request = new JsonBuilder(jsonMapData).toString()
		logger.info("Sending Record message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record....")
		def rows = sql.rows([recordId:recordId],config.harvest.sql.record.select)
		boolean hasOai = false
		boolean hasRif = false
		rows.each {rowEntry->
			assertNotNull(rowEntry)
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
			if ("oai_dc" == rowEntry.metadataPrefix)
				hasOai = true
			if ("rif" == rowEntry.metadataPrefix)
				hasRif = true
				
			assertTrue(hasOai || hasRif)
			assertNotNull(rowEntry.xmlEntry)
			def parsedXml = new XmlSlurper().parseText(rowEntry.xmlEntry)
			// validating header..
			assertEquals(jsonMapData.data[0].recordId, parsedXml.header.identifier.toString())
			assertEquals(jsonMapData.data[0].dateStamp, parsedXml.header.datestamp.toString())
			assertEquals("Parties_Groups", parsedXml.header.setSpec.toString())
			
			if ("oai_dc" == rowEntry.metadataPrefix) {
				logger.info("Validating OAI-DC")
				def oaiDc = parsedXml.metadata["dc"]
				
				assertEquals(jsonMapData.data[0].objectMetadata.localPid, oaiDc["identifier"].toString())
				String name = "${jsonMapData.data[0].metadata.data.Name}"
				assertEquals(name, oaiDc["title"].toString())
				assertEquals("'Parties' entry for '$name'", oaiDc["description"].toString())
			}
			if ("rif" == rowEntry.metadataPrefix) {
				logger.info("Validating RIF")
				def rif = parsedXml.metadata["registryObjects"]
				
				def primaryName = rif.registryObject.party.name.findAll{it.@type == "primary"}
				assertEquals(1, primaryName.size())
				
				assertEquals(jsonMapData.data[0].metadata.data.Name, primaryName[0].namePart.findAll{it.@type=="title"}[0].toString())
				
				// TODO: add more fields to check
			}
		}
		assertTrue(hasOai && hasRif)
		logger.info("Testing Record Group passed.")
	}
	// ---------------------------------------------------------------------------------------------------
	void doRecordService() {
		logger.info("-------------------------------------------------------------------------")
		logger.info("Testing for RB/Mint Data feed......Service")
		logger.info("-------------------------------------------------------------------------")
		def recordId = "db19981770633dc8656c84b2464bcb14"
		def mdPrefix = [ "rif"]
		def recordSource = "Unit-Test: Any arbitrary string that identifies the source of this publish request."
		def jsonMapData = [
			"header":[ // control header for identifying this record
				"type":"record_service"
			],
			"data":[ // the actual payload
				[ // first entry of the payload
					"recordId":recordId,
					"dateStamp":"2014-03-18T06:09:03Z",
					"metadataPrefix":mdPrefix,
					"source":recordSource,
					"metadata": [ // the actual data from RB/Mint/CM
						"recordIDPrefix": "redbox-mint.googlecode.com/services/",
					    "data": [
					        "ID": "1",
					        "Name": "University of Examples - ReDBox, Metadata Registry",
					        "Type": "assemble",
					        "ANZSRC_FOR_1": "",
					        "ANZSRC_FOR_2": "",
					        "ANZSRC_FOR_3": "",
					        "Location": "123 Example Street, City, STATE",
					        "Coverage_Temporal_From": "",
					        "Coverage_Temporal_To": "",
					        "Coverage_Spatial_Type": "",
					        "Coverage_Spatial_Value": "",
					        "Existence_Start": "",
					        "Existence_End": "",
					        "Website": "http://service.example.edu.au/",
					        "Data_Quality_Information": "http://service.example.edu.au/dataquality",
					        "Reuse_Information": "http://service.example.edu.au/reuse",
					        "Access_Policy": "http://service.example.edu.au/access",
					        "URI": "",
					        "Description": "ReDBox is a metadata registry application for describing research data."
					    ],
					    "metadata": [
					        "dc.identifier": "redbox-mint.googlecode.com/services/1"
					    ]
					],
					"objectMetadata":[
						"render-pending":false,
						"repository.type":"Services",
						"metaPid":"TF-OBJ-META",
						"jsonConfigOid":"f05ac885408193723707a78755bdeff",
						"jsonConfigPid":"Services.json",
						"repository.name":"Local Services",
						"rulesOid":"8cb984551ac43f9f745187d87ae3b1e7",
						"objectId":"db19981770633dc8656c84b2464bcb14",
						"scriptType":"python",
						"rulesPid":"Services.py"
						
					],
					"constants": [
						"oai_dc": [
							"curation":["pidProperty":"localPid"] // using the local Pid as identifier
						],
						"eac-cpf": [
							"curation":[
								"pidProperty":"localPid",
								"nlaIntegration":[
									"agencyCode":"AgencyCode",
									"agencyName":"AgencyName"
								]
							],
							"redbox.identity": [
								"institution": "University of Examples",
								"RIF-CS Group": "The University of Examples, Australia"
							]
						],
						"rif": [
							"urlBase":"http://localhost:9001/mint/",
							"curation":["pidProperty":"localPid"],
							"redbox.identity": [
								"institution": "University of Examples",
								"RIF-CS Group": "The University of Examples, Australia"
							]
						]
					]
				]
			]
		]
		def request = new JsonBuilder(jsonMapData).toString()
		logger.info("Sending Record message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record....")
		def rows = sql.rows([recordId:recordId],config.harvest.sql.record.select)
		boolean hasRif = false
		rows.each {rowEntry->
			assertNotNull(rowEntry)
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
			if ("rif" == rowEntry.metadataPrefix)
				hasRif = true
				
			assertTrue(hasRif)
			assertNotNull(rowEntry.xmlEntry)
			def parsedXml = new XmlSlurper().parseText(rowEntry.xmlEntry)
			// validating header..
			assertEquals(jsonMapData.data[0].recordId, parsedXml.header.identifier.toString())
			assertEquals(jsonMapData.data[0].dateStamp, parsedXml.header.datestamp.toString())
			assertEquals("Services", parsedXml.header.setSpec.toString())
			if ("rif" == rowEntry.metadataPrefix) {
				logger.info("Validating RIF")
				def rif = parsedXml.metadata["registryObjects"]
				
				def primaryName = rif.registryObject.service.name.findAll{it.@type == "primary"}
				assertEquals(1, primaryName.size())
				
				assertEquals(jsonMapData.data[0].metadata.data.Name, primaryName[0].namePart.findAll{it.@type=="title"}[0].toString())
				
				// TODO: add more fields to check
			}
		}
		assertTrue(hasRif)
	}
}
