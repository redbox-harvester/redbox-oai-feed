@Grab(group='net.sf.gtools.jms', module='JmsCategory', version='0.2')
@Grab(group='org.apache.activemq',module = 'activemq-all', version='5.9.0')
import org.apache.activemq.ActiveMQConnectionFactory
import net.sf.gtools.jms.JmsCategory

class GroovyJMSExample {
    def sendMessage(url) {
        use(JmsCategory) {
            def jms = new ActiveMQConnectionFactory('tcp://localhost:9301')
            jms.connect { c ->
                c.queue("oaiPmhFeed") { q ->
                    String txt = url.toURL().text
                    def msg = createTextMessage(txt)
                    q.send(msg)
                }
            }
        }
    }
    static void main(String[] args) {
		if (!args[0]) {
			println 'Specify the URL of the test data to send.'
			return
		}
        new GroovyJMSExample().sendMessage(args[0])
    }
}