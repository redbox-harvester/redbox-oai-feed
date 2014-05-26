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

import groovy.json.*
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
		doRecordDataset()
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
		// read the sample file and use its data
		def sampleFile = new File("support/install/sampleCurationManagerEacPerson.json")
		def jsonMapData = new JsonSlurper().parse(sampleFile)
		def recordId = jsonMapData.data[0].recordId.toString()
		def recordSource = jsonMapData.data[0].source.toString()
		def request = sampleFile.text

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
		// read the sample file and use its data
		def sampleFile = new File("support/install/sampleRedboxMintPeople.json")
		def jsonMapData = new JsonSlurper().parse(sampleFile)
		def recordId = jsonMapData.data[0].recordId.toString()
		def recordSource = jsonMapData.data[0].source.toString()
		def mdPrefix = jsonMapData.data[0].metadataPrefix
		def request = sampleFile.text
		
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
		// read the sample file and use its data
		def sampleFile = new File("support/install/sampleRedboxMintGroup.json")
		def jsonMapData = new JsonSlurper().parse(sampleFile)
		def recordId = jsonMapData.data[0].recordId.toString()
		def recordSource = jsonMapData.data[0].source.toString()
		def mdPrefix = jsonMapData.data[0].metadataPrefix
		def request = sampleFile.text
		
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
		// read the sample file and use its data
		def sampleFile = new File("support/install/sampleRedboxMintService.json")
		def jsonMapData = new JsonSlurper().parse(sampleFile)
		def recordId = jsonMapData.data[0].recordId.toString()
		def recordSource = jsonMapData.data[0].source.toString()
		def mdPrefix = jsonMapData.data[0].metadataPrefix
		def request = sampleFile.text
		
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
	
	// ---------------------------------------------------------------------------------------------------
	void doRecordDataset() {
		logger.info("-------------------------------------------------------------------------")
		logger.info("Testing for RB/Mint Data feed......Dataset")
		logger.info("-------------------------------------------------------------------------")
		// read the sample file and use its data
		def sampleFile = new File("support/install/sampleRedboxMintDataset.json")
		def jsonMapData = new JsonSlurper().parse(sampleFile)
		def recordId = jsonMapData.data[0].recordId.toString()
		def recordSource = jsonMapData.data[0].source.toString()
		def mdPrefix = jsonMapData.data[0].metadataPrefix
		def request = sampleFile.text
		
		logger.info("Sending Record message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record....")
		def rows = sql.rows([recordId:recordId],config.harvest.sql.record.select)
		boolean hasRif = false
		boolean hasOai = false
		rows.each {rowEntry->
			assertNotNull(rowEntry)
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
			if ("rif" == rowEntry.metadataPrefix)
				hasRif = true
			if ("oai_dc" == rowEntry.metadataPrefix) {
				hasOai = true
			}
				
			assertTrue(hasRif || hasOai)
			assertNotNull(rowEntry.xmlEntry)
			def parsedXml = new XmlSlurper().parseText(rowEntry.xmlEntry)
			// validating header..
			assertEquals(jsonMapData.data[0].recordId, parsedXml.header.identifier.toString())
			assertEquals(jsonMapData.data[0].dateStamp, parsedXml.header.datestamp.toString())
			assertEquals("Dataset", parsedXml.header.setSpec.toString())
			if ("rif" == rowEntry.metadataPrefix) {
				logger.info("Validating RIF")
				def rif = parsedXml.metadata["registryObjects"]
				
				def primaryName = rif.registryObject.collection.name.findAll{it.@type == "primary"}
				assertEquals(1, primaryName.size())
//				
				assertEquals(jsonMapData.data[0].metadata["dc:title"], primaryName[0].namePart.toString())
				
				// TODO: add more fields to check
			}
			if ("oai_dc" == rowEntry.metadataPrefix) {
				logger.info("Validating OAI-DC")
				def oaiDc = parsedXml.metadata["dc"]
				
				assertEquals(jsonMapData.data[0].objectMetadata.localPid, oaiDc["identifier"].toString())
				assertEquals(jsonMapData.data[0].metadata["dc:title"], oaiDc["title"].toString())
				
			}
		}
		assertTrue(hasRif && hasOai)
	}
}
