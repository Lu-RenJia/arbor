package com.arbor.groovy.util;

import groovy.lang.Closure;
import groovy.xml.MarkupBuilder;
import groovy.xml.XmlUtil;

import java.io.StringWriter;

/**
 * Groovy XML 快捷工具 — 注入 Groovy Binding 供脚本直接使用
 *
 * @author gwl
 */
public class GroovyXmlUtil {

    /**
     * 用 MarkupBuilder 构建 XML 字符串。
     * Groovy 脚本调用示例：
     * <pre>{@code
     * return [fileType: "XML", data: xmlUtil.builder {
     *     users {
     *         rows.each { row -> user(name: row.姓名, age: row.年龄) }
     *     }
     * }]
     * }</pre>
     */
    public String builder(Closure<?> closure) {
        StringWriter writer = new StringWriter();
        MarkupBuilder mb = new MarkupBuilder(writer);
        closure.setDelegate(mb);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return writer.toString();
    }

    /**
     * 将 XmlParser/XmlSlurper 返回的 Node/GPathResult 序列化为 XML 字符串
     */
    public String serialize(Object node) {
        if (node instanceof groovy.util.Node) {
            return XmlUtil.serialize((groovy.util.Node) node);
        }
        if (node instanceof groovy.xml.slurpersupport.GPathResult) {
            return XmlUtil.serialize((groovy.xml.slurpersupport.GPathResult) node);
        }
        return node != null ? node.toString() : "";
    }
}
