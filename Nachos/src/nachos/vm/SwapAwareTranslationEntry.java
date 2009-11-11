package nachos.vm;

import nachos.machine.TranslationEntry;

public class SwapAwareTranslationEntry
{
    /**
     * Initializes all integer values to -1,
     * all booleans to false except readOnly
     * and all object references to null.
     */
    public SwapAwareTranslationEntry() {
        vpn = -1;
        ppn = -1;
        inSwap = false;
        isStack = false;
        valid = false;
        readOnly = true;
        used = false;
        dirty = false;
        swapPageNumber = -1;
        isCoff = false;
        coffSection = -1;
        coffPage = -1;
        pid = -1;
    }

    /**
     * Honors the same rules as {@link SwapAwareTranslationEntry()} but
     * then initializes the fields to the values found in entry.
     * @param entry the entry who's values we should assume.
     */
    public SwapAwareTranslationEntry(TranslationEntry entry) {
        this();
        vpn = entry.vpn;
        ppn = entry.ppn;
        valid = entry.valid;
        readOnly = entry.readOnly;
        used = entry.used;
        dirty = entry.dirty;
    }

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

    @Override
    public String toString() {
        return "SATE[vpn=" + vpn
                +";ppn=" + ppn
                +";valid=" + valid
                +";readOnly=" + readOnly
                +";used=" + used
                +";dirty=" + dirty
                +";inSwap=" + inSwap
                +";swapPN=" + swapPageNumber
                +";stack?"+isStack
                +";coff?"+isCoff
                +";coffSection="+coffSection
                +";coffPage="+coffPage+"]\r\n";
    }

    /** The virtual page number. */
    public int vpn;

    /** The physical page number. */
    public int ppn;

    /**
     * If this flag is <tt>false</tt>, this translation entry is ignored.
     */
    public boolean valid;

    /**
     * If this flag is <tt>true</tt>, the user pprogram is not allowed to
     * modify the contents of this virtual page.
     */
    public boolean readOnly;

    /**
     * This flag is set to <tt>true</tt> every time the page is read or written
     * by a user program.
     */
    public boolean used;

    /**
     * This flag is set to <tt>true</tt> every time the page is written by a
     * user program.
     */
    public boolean dirty;
    /** Indicates this page housed in swap. */
    public boolean inSwap;
    /** Contains the page number within the swap file where this page lives. */
    public int swapPageNumber;
    /**
     * Indicates this page is a stack page
     * and thus cannot be reloaded from a COFF,
     * and must be initialized on first use.
     */
    public boolean isStack;
    public boolean isCoff;
    /**
     * Contains the <em>optional</em> COFF section which contains this page's data.
     * Be advised that a COFF section is usually wider than a single page, so
     * you'll need to consult {@link #coffPage} for any loads.
     */
    public int coffSection;
    /**
     * Identifies the specific page within the COFF section
     * that contains this page's data.
     */
    public int coffPage;
    
    public int pid;
}
