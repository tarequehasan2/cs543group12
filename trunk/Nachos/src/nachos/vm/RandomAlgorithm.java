package nachos.vm;

import java.util.List;
import java.util.Map;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Lock;

public final class RandomAlgorithm implements Algorithm {

	private final Map<Integer, List<SwapAwareTranslationEntry>> coreMap;
	private final Lock coreMapLock;

	RandomAlgorithm(Map<Integer, List<SwapAwareTranslationEntry>> coreMap, Lock coreMapLock ){
		this.coreMap = coreMap;
		this.coreMapLock = coreMapLock;
	}
	
	@Override
	public int findVictim() {
		int victim = Lib.random(Machine.processor().getNumPhysPages());
		coreMapLock.acquire();
		Lib.assertTrue(coreMap.containsKey(victim));
		coreMapLock.release();
		return victim;
	}
	
}
