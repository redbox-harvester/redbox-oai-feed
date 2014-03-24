@Grab(group='net.sf.gtools.jms', module='JmsCategory', version='0.2')
@Grab(group='org.apache.activemq',module = 'activemq-all', version='5.9.0')
import org.apache.activemq.ActiveMQConnectionFactory
import net.sf.gtools.jms.JmsCategory

class GroovyJMSExample {
    def sendMessage() {
        use(JmsCategory) {
            def jms = new ActiveMQConnectionFactory('tcp://localhost:9301')
            jms.connect { c ->
                c.queue("oaiPmhFeed") { q ->
                    def txt = """
                    {"header":{"type":"record_people"},"data":[{"recordId":"record-1: a unique DB identifier, could be OID","metadataPrefix":["eac-cpf","oai_dc"],"source":"Unit-Test: Any arbitrary string that identifies the source of this publish request.","jsonData":{"recordId":"d082b0890570265c99b52f360a674112","control":{"maintenanceAgency":{"agencyCode":"TO-DO","agencyName":"The University of Examples, Australia"},"maintenanceHistory":{"maintenanceEvent":{"eventDateTime_standardDateTime":"","agent":"Mint Name Authority - The University of Examples, Australia"}}},"entityId":"http://demo.redboxresearchdata.com.au/mint/published/detail/d082b0890570265c99b52f360a674112","surname":"Zweinstein","forename":"Alberto","description":"Dr Alberto Zweinstein is a Lecturer at the University of Examples","salutation":"Mr","dateStamp":"2014-03-18T06:09:03Z"}}]}
                    """
                    def msg = createTextMessage(txt.toString())
                    q.send(msg)
                }
            }
        }
    }
    static void main(String[] args) {
        new GroovyJMSExample().sendMessage()
    }
}