package nachos.vm;

import nachos.machine.Lib;
import nachos.machine.Machine;

public final class RandomAlgorithm implements Algorithm {

	RandomAlgorithm(){
	}

	@Override
	public int findVictim() {
		int result;
		result = Lib.random(Machine.processor().getNumPhysPages());
		return result;
	}

}
