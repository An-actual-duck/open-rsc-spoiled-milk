package com.openrsc.server.io;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import java.io.File;

/** Loads the built paired client's definitions, not a text-only sprite/ID assertion. */
public final class ConfiguredAgilityClientDefinitionTest {
	public static void main(String[] arguments) throws Exception {
		EntityHandler.load(true);
		NodeList definitions=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(arguments[0])).getDocumentElement().getElementsByTagName("GameObjectDef");
		if(EntityHandler.objectCount()!=definitions.getLength())throw new AssertionError("Server/client scenery count drift");
		int found=0;
		for(int id=0;id<definitions.getLength();id++) {
			Element server=(Element)definitions.item(id);
			String action=text(server,"command1").toLowerCase(java.util.Locale.ROOT);
			if(!action.matches("agility-(jump|crawl|climb|swing|balance)"))continue;
			found++;
			GameObjectDef client=EntityHandler.getObjectDef(id);
			if(!client.getName().equals(text(server,"name")) || !client.getCommand1().equals(text(server,"command1"))
				|| !client.getCommand2().equals(text(server,"command2")) || !client.getObjectModel().equals(text(server,"objectModel"))
				|| client.getType()!=0 || client.getWidth()!=Integer.parseInt(text(server,"width")) || client.getHeight()!=Integer.parseInt(text(server,"height")) || client.modelID<0)
				throw new AssertionError("Generic scenery does not match the built paired client at "+id);
		}
		if(found!=5)throw new AssertionError("Expected five automatically discoverable generic definitions");
		System.out.println("PASS built paired client generic agility scenery: matching discovered ID, command, nonblocking footprint and existing model selector");
	}
	private static String text(Element value,String field){return value.getElementsByTagName(field).item(0).getTextContent();}
}
