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
	def systemConfig
	
	public String encodeXml(String str) {
		return XmlUtil.escapeXml(str)
	}
	/**
	 * Replaces $systemConfig.getString
	 * 
	 */
	public String getString(String defaultStr, String... fields) {
		if (!systemConfig) {
			systemConfig = new JsonSimpleWrapper(data.constants[format])
		}
		return systemConfig.getString(defaultStr, fields)
	}
	
	/**
	 * Replaces $util.get, item must refer the 'data.metadata'
	 * 
	 */
	public String get(JsonSimpleWrapper item, String... fields) {	
		item.getString("", fields)
	}
	/**
	 * Replaces $util.getMetadata - object must refer the 'data.objectMetadata'
	 */
	public String getMetadata(JsonSimpleWrapper object, String property) {
		object.getString("", property)
	}
	/**
	 * Replaces $util.getList
	 * 
	 */
	public Map<String, Object> getList(JsonSimpleWrapper object, String property) {
		def retVal = [:]
		property = property.endsWith(".") ? property : property + "."
		object.data.each{ key,value->
			if (key.startsWith(property)) {
				def data = null
				String field = property

                if (key.length() >= property.length()) {
                    field = key.substring(property.length(), key.length())
                }

                String index = field;
                if (field.indexOf(".") > 0) {
                    index = field.substring(0, field.indexOf("."))
                }
				if (retVal.containsKey(index)) {
					data = retVal[index]
				} else {
					data = [:]
					retVal.put(index, data)
				}

				if (value.length() == 1) {
					value = String.valueOf(value.charAt(0))
				}

				data.put(
						field.substring(field.indexOf(".") + 1, field.length()),
						value);
			}
		}
		return retVal
	}
	
	public JsonSimpleWrapper getItem() {
		return new JsonSimpleWrapper(data.metadata)
	}
	
	public JsonSimpleWrapper getObject() {
		return new JsonSimpleWrapper(data.objectMetadata)
	}
}
/**
 * To prevent backbreaking porting. PLEASE DO NOT USE IN NEW CODE. PLEASE.
 */
class JsonSimpleWrapper {
	def data
	
	public JsonSimpleWrapper(data) {
		this.data = data
	}
	
	public List<JsonSimpleWrapper> getJsonSimpleList(String... fields) {
		def dList = getObject(null, fields)
		List list = []
		if (dList) {
			dList.each {d->
				list << new JsonSimpleWrapper(d)
			}
		}
		return list
	}
	
	public boolean getBoolean(boolean retVal, String... fields) {
		def obj = getObject(retVal, fields)
		if (obj) {
			if (obj instanceof String) {
				retVal = Boolean.parseBoolean(obj)
			} else {
				// no more number checking, etc. beware!
				retVal = obj
			}
		}
		return retVal
	}
	
	public String getString(String retVal, String... fields) {
		return getObject(retVal, fields)
	}
	/**
	 * Convenience method for developer sanity.
	 *
	 */
	def getObject(retVal, String... fields) {
		StringBuilder fullFieldBuff = new StringBuilder()
		fields.each {field->
			fullFieldBuff.append('["')
			fullFieldBuff.append(field)
			fullFieldBuff.append('"]')
		}
		def val = Eval.x(data, "x${fullFieldBuff.toString()}")
		return val ? val : retVal
	}
}