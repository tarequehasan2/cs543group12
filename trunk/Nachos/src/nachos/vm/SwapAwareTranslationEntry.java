package nachos.vm;

import nachos.machine.TranslationEntry;

public class SwapAwareTranslationEntry
{
    /**
     * Initializes all integer values to -1,
     * all booleans to false except readOnly
     * and all object references to null.
     */
    private SwapAwareTranslationEntry() {
        vpn = -1;
        ppn = -1;
        inSwap = false;
        isStack = false;
        valid = false;
        readOnly = true;
        used = false;
        dirty = false;
        swapPageNumber  = -1;
        isCoff = false;
        coffSection = -1;
        coffPage = -1;
    }

    public SwapAwareTranslationEntry(int vpn, boolean isStack) {
    	this();
    	this.vpn = vpn;
        this.isCoff = false;
    	this.isStack = isStack;
    	this.readOnly = false;
    }

    public SwapAwareTranslationEntry(int vpn, boolean readOnly, int coffSection, int coffPage) {
    	this();
    	this.vpn = vpn;
    	this.readOnly = readOnly;
    	this.isStack = false;
        this.isCoff = true;
    	this.coffSection = coffSection;
    	this.coffPage = coffPage;
    }

    /**
     * Honors the same rules as {@link SwapAwareTranslationEntry()} but
     * then initializes the fields to the values found in entry.
     * @param entry the entry who's values we should assume.
     */
//    private SwapAwareTranslationEntry(TranslationEntry entry) {
//        this();
//        vpn = entry.vpn;
//        ppn = entry.ppn;
//        valid = entry.valid;
//        readOnly = entry.readOnly;
//        used = entry.used;
//        dirty = entry.dirty;
//    }

    /**
     * Adapts this object back to a <em>new instance</em> of a TranslationEntry.
     * @return a newly constructed TranslationEntry which contains a subset
     * of this object's values.
     */
    public TranslationEntry toTranslationEntry() {
        TranslationEntry result = new TranslationEntry();
        result.vpn = vpn;
        result.ppn = ppn;
        result.valid = valid;
        result.readOnly = readOnly;
        result.used = used;
        result.dirty = dirty;
        return result;
    }

    public void ejectedFromMemory() {
    	this.valid = false;
    	this.ppn = -1;
    }

	public void movedToSwap(int swapPageNumber) {
		this.swapPageNumber = swapPageNumber;
		this.inSwap = true;
    	this.valid = false;
    	this.ppn = -1;
	}

	public void restoredToMemory(int ppn) {
        this.ppn = ppn;
    	valid = true;
        // purposefully leave inSwap and swapPageNumber alone
    }

    public void removedFromSwapfile() {
        inSwap = false;
        swapPageNumber = -1;
    }

	public void clearUsedMark() {
		used = false;
		dirty = false;
	}

	public void markAsUsed() {
		used = true;
	}

	public void markAsDirty() {
		used = true;
		dirty = true;
	}

    public boolean isCoff() {
//		return !isStack && -1 != coffSection && -1 != coffPage;
        return isCoff;
	}

    public int getVpn() {
		return vpn;
	}

    public int getPpn() {
		return ppn;
	}

    public boolean isValid() {
		return valid;
	}

    public boolean isStack() {
		return isStack;
	}

    public boolean isReadOnly() {
		return readOnly;
	}

    public boolean isDirty() {
		return dirty;
	}

    public boolean isUsed() {
		return used;
	}

    public int getCoffSection() {
		return coffSection;
	}

	public int getCoffPage() {
		return coffPage;
	}

	public int getSwapPageNumber() {
		return swapPageNumber;
	}

	public boolean isInSwap() {
		return inSwap;
	}

    @Override
    public String toString() {
        return "SATE[ vpn=" + vpn
                +" ppn=" + ppn
                +" valid=" + valid
                +" readOnly=" + readOnly
                +" used=" + used
                +" dirty=" + dirty
                +" inSwap=" + isInSwap()
                +" swapPN=" + getSwapPageNumber()
                +" stack?"+isStack
                +" coff?"+isCoff
                +" coffSection="+getCoffSection()
                +" coffPage="+getCoffPage()+"]";
    }


	/** The virtual page number. */
    private int vpn;

    /** The physical page number. */
    private int ppn;

    /**
     * If this flag is <tt>false</tt>, this translation entry is ignored.
     */
    private boolean valid;

    /**
     * If this flag is <tt>true</tt>, the user pprogram is not allowed to
     * modify the contents of this virtual page.
     */
    private boolean readOnly;

    /**
     * This flag is set to <tt>true</tt> every time the page is read or written
     * by a user program.
     */
    private boolean used;

    /**
     * This flag is set to <tt>true</tt> every time the page is written by a
     * user program.
     */
    private boolean dirty;
    /** Indicates this page housed in swap. */
    private boolean inSwap;
    /** Contains the page number within the swap file where this page lives. */
    private int swapPageNumber;
    /**
     * Indicates this page is a stack page
     * and thus cannot be reloaded from a COFF,
     * and must be initialized on first use.
     */
    private boolean isStack;
    private boolean isCoff;
    /**
     * Contains the <em>optional</em> COFF section which contains this page's data.
     * Be advised that a COFF section is usually wider than a single page, so
     * you'll need to consult {@link #coffPage} for any loads.
     */
    private int coffSection;
    /**
     * Identifies the specific page within the COFF section
     * that contains this page's data.
     */
    private int coffPage;
}
