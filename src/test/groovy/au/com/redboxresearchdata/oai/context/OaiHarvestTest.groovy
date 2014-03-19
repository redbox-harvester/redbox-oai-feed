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
	
	void testHarvest() {
		new File ("target/test/db/").deleteDir()
		def config = new ConfigSlurper("test").parse(new File("src/test/resources/harvester-config-test.groovy").toURI().toURL())
		def grailsConfig = ["environment":"test", "clientConfigObj":config]		
		def grailsApplication = [:]		
		grailsApplication.config = grailsConfig
		
		MBeanServerFactoryBean mbeanServer = new MBeanServerFactoryBean()
		DefaultListableBeanFactory parentBeanFactory = new DefaultListableBeanFactory()
		parentBeanFactory.registerSingleton("grailsApplication", grailsApplication)
		parentBeanFactory.registerSingleton("mbeanServer", mbeanServer)
		GenericApplicationContext parentContext = new GenericApplicationContext(parentBeanFactory)
		parentContext.refresh()
		
		logger.info("Starting SI...")
		String[] locs = ["file:"+config.client.siPath]
		def appContext = new FileSystemXmlApplicationContext(locs, true, parentContext)
		appContext.registerShutdownHook()		
		def sql = new Sql((DataSource)appContext.getBean("dataSource"))		 
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
		final MessageChannel oaiHarvestMainChannel = appContext.getBean("oaiHarvestMainChannel", MessageChannel.class)
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
		logger.info("Testing Record")
		logger.info("Creating DB table:${config.harvest.sql.record.init}")
		sql.execute(config.harvest.sql.record.init)
		def recordSource = "Unit-Test: Any arbitrary string that identifies the source of this publish request."
		def recordId = "record-1: a unique DB identifier, could be OID"
		def mdPrefix = ["eac-cpf", "oai_dc"]
		def jsonMapData = [
			"header":[
				"type":"record_people"
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
		request = new JsonBuilder(jsonMapData).toString()
		logger.info("Sending Record message....")
		logger.debug(request)
		oaiHarvestMainChannel.send(MessageBuilder.withPayload(request).build())
		logger.info("Validating Record....")
		def rows = sql.rows(config.harvest.sql.record.select, [recordId])
		rows.each {rowEntry->
			assertNotNull(rowEntry)		
			assertEquals(recordId, rowEntry.recordId)
			assertEquals(recordSource, rowEntry.source)
	//		assertEquals(metadataPrefix, rowEntry.metadataPrefix)
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
		logger.info("Record passed.")
		logger.info("Testing Identity")
		logger.info("Creating DB table:${config.harvest.sql.identify.init}")
		sql.execute(config.harvest.sql.identify.init)
		def xmlEntry = "<Identify>    <repositoryName>The Fascinator</repositoryName>    <baseURL>http://demo.redboxresearchdata.com.au/mint/default</baseURL>    <protocolVersion>2.0</protocolVersion>    <adminEmail>fascinator@usq.edu.au</adminEmail>      <earliestDatestamp>0001-01-01T00:00:00Z</earliestDatestamp>    <deletedRecord>persistent</deletedRecord>    <granularity>YYYY-MM-DDThh:mm:ssZ</granularity>    <description>        <oai-identifier xsi:schemaLocation='http://www.openarchives.org/OAI/2.0/oai-identifier http://www.openarchives.org/OAI/2.0/oai-identifier.xsd' xmlns='http://www.openarchives.org/OAI/2.0/oai-identifier'>            <scheme>oai</scheme>            <repositoryIdentifier>fascinator.usq.edu.au</repositoryIdentifier>            <delimiter>:</delimiter>            <sampleIdentifier>oai:fascinator.usq.edu.au:5e8ff9bf55ba3508199d22e984129be6</sampleIdentifier>        </oai-identifier>    </description></Identify> "
		request = """
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
		row = sql.firstRow(config.harvest.sql.identify.select)
		assertNotNull(row)
		assertEquals(xmlEntry.toString(), row.xmlEntry)
		logger.info("Identity passed.")		
		logger.info("Testing Sets")
		logger.info("Creating DB table:${config.harvest.sql.set.init}")
		sql.execute(config.harvest.sql.set.init)
		def spec = "Parties_People"
		xmlEntry = "<set><setSpec>Parties_People</setSpec><setName>Parties - People</setName></set>"
		request = """
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
		row = sql.firstRow(config.harvest.sql.set.select)
		assertNotNull(row)
		assertEquals(xmlEntry.toString(), row.xmlEntry)
		assertEquals(spec, row.spec)
		
		logger.info("Test success! Shutting down SI...")
		def mbeanExporter = appContext.getBean("mbeanExporterOaiFeed")
		mbeanExporter.stopActiveComponents(false, 5000)
		logger.info("Shutdown command success.")
	}
}
