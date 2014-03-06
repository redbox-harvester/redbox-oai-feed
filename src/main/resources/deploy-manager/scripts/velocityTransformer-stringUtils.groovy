import groovy.xml.XmlUtil

data["stringUtil"] = new StringUtil()
message = "Added StringUtil instance."

class StringUtil {
	public String escapeXml(String str) {
		return XmlUtil.escapeXml(str)
	}
}