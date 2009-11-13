package nachos.vm;

import java.util.List;
import java.util.Map;

import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.threads.Lock;

public final class RandomAlgorithm implements Algorithm {

	RandomAlgorithm(Map<Integer, List<SwapAwareTranslationEntry>> coreMap, Lock coreMapLock ){
	}

	@Override
	public int findVictim() {
		int result;
		result = Lib.random(Machine.processor().getNumPhysPages());
		return result;
	}

}
