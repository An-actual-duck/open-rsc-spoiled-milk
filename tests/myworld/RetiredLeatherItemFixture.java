import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;

public final class RetiredLeatherItemFixture {
	public static void main(String[] args) {
		orsc.Config.S_WANT_BANK_NOTES=true;
		orsc.Config.S_WANT_CERT_AS_NOTES=true;
		EntityHandler.load(true);
		int count=0;
		for(int[] range:new int[][]{{1807,1816},{1875,1884},{1895,1904},{1915,1919}})
			for(int id=range[0];id<=range[1];id++) {
				ItemDef item=EntityHandler.getItemDef(id), note=EntityHandler.getItemDef(id,true);
				if(item==null || !item.untradeable || !item.noteable || note==null || !note.untradeable
					|| !note.stackable || !item.getName().equals(note.getName())) throw new AssertionError("Legacy item/note "+id);
				count++;
			}
		for(int id:new int[]{1795,1796,1801,1802,1839,1854,1859,3399})
			if(EntityHandler.getItemDef(id).untradeable) throw new AssertionError("Unrelated binding "+id);
		System.out.println("PASS: "+count+" retired client definitions and note forms bound; unrelated materials retained");
	}
}
