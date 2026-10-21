package com.openrsc.server.io;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.GameObjectDef;
import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Cold-load the actual built client and pair its discovered rows with server literals. */
public final class DoubleDoorClientDefinitionTest {
	public static void main(String[] args) throws Exception {
		EntityHandler.load(true);
		NodeList rows=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(args[0])).getDocumentElement().getElementsByTagName("GameObjectDef");
		if(EntityHandler.objectCount()!=rows.getLength())throw new AssertionError("Client/server scenery count differs");
		int count=0;
		for(int id=0;id<rows.getLength();id++) {
			Element row=(Element)rows.item(id);
			if(!value(row,"name").equals("Pass-through double doors"))continue;
			GameObjectDef client=EntityHandler.getObjectDef(id);count++;
			if(!client.getName().equals(value(row,"name"))||!client.getDescription().equals(value(row,"description"))
				||!client.getCommand1().equals(value(row,"command1"))||!client.getCommand2().equals(value(row,"command2"))
				||!client.getObjectModel().equals(value(row,"objectModel"))||client.getType()!=2||client.getWidth()!=1||client.getHeight()!=2||client.modelID<0)
				throw new AssertionError("Paired door model/action/collision differs at discovered row "+id);
		}
		if(count!=2)throw new AssertionError("Expected one closed and one temporary-open discovered row");
		System.out.println("PASS built client/server discovered generic double-door definitions, existing models, both blocking 1x2 footprints and commands");
	}
	private static String value(Element row,String name){return row.getElementsByTagName(name).item(0).getTextContent();}
}
