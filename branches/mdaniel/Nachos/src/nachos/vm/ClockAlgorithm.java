package nachos.vm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Lock;

public class ClockAlgorithm implements Algorithm{

	private final Map<Integer,Integer> victims = new HashMap<Integer, Integer>();
	private final Map<Integer, List<SwapAwareTranslationEntry>> coreMap;
	private final Lock coreMapLock;

	ClockAlgorithm(Map<Integer, List<SwapAwareTranslationEntry>> coreMap, Lock coreMapLock ){
		this.coreMap = coreMap;
		this.coreMapLock = coreMapLock;
	}

	@Override
	public int findVictim() {
		int longestUnused = 0;
		int nextVictim = -1;
		int anyUnused = -1;
		coreMapLock.acquire();
		// look through all physical pages (clock starting at 0)
		for (int i=0; i <  Machine.processor().getNumPhysPages(); i++){
			List<SwapAwareTranslationEntry> swapAwareTranslationEntries = coreMap.get(i);
			boolean used = false;
			for (SwapAwareTranslationEntry swapAwareTranslationEntry : swapAwareTranslationEntries){
				// If it is used, mark it not used, so that next time we know whether it is used.
				if (swapAwareTranslationEntry.isUsed()){
					used = true;
					swapAwareTranslationEntry.clearUsedMark();
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
			if (longestUnused < victims.get(i)){
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
					nextVictim = Lib.random(Machine.processor().getNumPhysPages());
				}
			}
		}
		coreMapLock.release();
		return nextVictim;
	}



}
