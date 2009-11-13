package nachos.vm;

import java.util.HashMap;
import java.util.Map;

import nachos.machine.Lib;
import nachos.machine.Machine;

public class ClockAlgorithm implements Algorithm{

	private final Map<Integer,Integer> victims = new HashMap<Integer, Integer>();

	@Override
	public int findVictim() {
		// get the TLB all synced up
		InvertedPageTable.syncAllProcTlb();
		int longestUnused = 0;
		int nextVictim = -1;
		int anyUnused = -1;
        final int numPhysPages = Machine.processor().getNumPhysPages();
        // look through all physical pages (clock starting at 0)
        for (int i=0; i < numPhysPages; i++){
			boolean used = false;
			for (CoreMap.CoreMapEntry coreEntry : CoreMap.findEntriesForPpn(i)){
                SwapAwareTranslationEntry entry
                        = InvertedPageTable.findEntryForVpn(
                            coreEntry.getPid(), coreEntry.getVpn());
				// If it is used, but not dirty, mark it not used, so that next time we know whether it is used.
				if (entry.isUsed() && !entry.isDirty()){
					used = true;
					entry.clearUsedMark();
				}
			}
			// If not used, make a possible victim
			if (used == false){
				if (victims.containsKey(i)){
					int value = victims.get(i);
					value++;
					victims.put(i, value);
				}else{
					victims.put(i, 1);
				}
				// In case we don't know the longest unused, we'll pick the last one examined.
				anyUnused = i;
			// If used, make it less likely of a victim
			}else {
				if (victims.containsKey(i)){
					int value = victims.get(i);
					if (value == 1){
						victims.remove(i);
					}else{
						value--;
						victims.put(i, value);
					}
				}
			}
			// Since we are iterating anyway, get the longest unused victim
			if (victims.containsKey(i) && longestUnused < victims.get(i)){
				longestUnused = victims.get(i);
				nextVictim = i;
				
			}

			// no unused pages??
			if (nextVictim == -1){
				//TODO find minimally used?
				if (anyUnused > -1){
					nextVictim = anyUnused;
				}else{
					// NO Unused pages??  OK.  I pick this one.
					nextVictim = Lib.random(numPhysPages);
				}
			}
		}
        victims.remove(Integer.valueOf(nextVictim));
 		return nextVictim;
	}



}
