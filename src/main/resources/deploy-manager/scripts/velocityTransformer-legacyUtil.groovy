import groovy.xml.XmlUtil
import groovy.util.Eval
import org.apache.log4j.Logger

data["util"] = new LegacyUtil()
data["systemConfig"] = data["util"]
message = "Added Legacy Utility instance to template."

/**
 * 
 * Class for providing methods for the legacy templates. DO NOT USE ON NEW TEMPLATES, PLEASE.
 * 
 * Note that this class relies on a specific data format - consult doco or the test class.
 * 
 * @author <a href="https://github.com/shilob">Shilo Banihit</a>
 * 
 */
class LegacyUtil {
	private static final Logger logger = Logger.getLogger(LegacyUtil.class)
		
	def data
	def format
	
	public String encodeXml(String str) {
		return XmlUtil.escapeXml(str)
	}
	/**
	 * Replaces $systemConfig.getString
	 * 
	 */
	public String getString(String defaultStr, String... fields) {
		return extractString(data.constants[format], defaultStr, fields)
	}
	
	/**
	 * Replaces $util.get
	 * 
	 */
	public String get(item, String... fields) {
		extractString(data.metadata, "", fields)
	}
	/**
	 * Replaces $util.getMetadata
	 */
	public String getMetadata(object, String property) {
		extractString(data.objectMetadata, "", property)
	}
	/**
	 * Convenience method for developer sanity.
	 * 
	 */
	private String extractString(obj, String defaultStr, String... fields) {
		StringBuilder fullFieldBuff = new StringBuilder()
		fields.each {field->
			fullFieldBuff.append('["')
			fullFieldBuff.append(field)
			fullFieldBuff.append('"]')
		}
		String val = Eval.x(obj, "x${fullFieldBuff.toString()}")
		return val ? val : defaultStr
	}
}