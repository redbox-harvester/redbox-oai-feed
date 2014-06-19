@Grab(group='net.sf.gtools.jms', module='JmsCategory', version='0.2')
@Grab(group='org.apache.activemq',module = 'activemq-all', version='5.9.0')
import org.apache.activemq.ActiveMQConnectionFactory
import net.sf.gtools.jms.JmsCategory

class GroovyJMSExample {
    def sendMessage(url, port, queueName) {
        use(JmsCategory) {
			port = port ? port : '9301'
			queueName = queueName ? queueName : 'oaiPmhFeed'
            def jms = new ActiveMQConnectionFactory('tcp://localhost:'+port)
            jms.connect { c ->
                c.queue(queueName) { q ->
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
		def port = args.size() > 1 ? args[1] : null
		def queueName = args.size() > 2 ? args[2] : null
		new GroovyJMSExample().sendMessage(args[0], port, queueName)
    }
}