package beast.tree;

import beast.core.Input;
import beast.core.parameter.IntegerParameter;
import beast.evolution.alignment.CodonAlignment;

import java.io.PrintStream;

/**
 * The large 2-d matrix to store internal node states.
 * Rows are internal nodes, cols are sites.
 * The values are flattened into 1d <code>int[]</code>.<br>
 *
 * <code>minorDimension</code> is the number of codons.<br>
 * <code>internalNodeCount</code> is the number of internal nodes.<br>
 * <code>nodeNr</code> is the node index number starting from 0,
 * and for internal nodes, they are ranged from the number of leaf nodes
 * to the total nodes - 1.<br>
 * So use the following formula to convert index:<br>
 * <code>rowIndex = nodeNr - internalNodeCount - 1</code><br>
 *
 */
public class InternalNodeStates extends IntegerParameter {

    final public Input<CodonAlignment> dataInput = new Input<>("data",
            "codon alignment to initialise the 2-d matrix of internal node states", Input.Validate.REQUIRED);

    // row (x) is internal nodes = leafNodeCount - 1
    protected int internalNodeCount = -1;

    @Override
    public void initAndValidate() {
        CodonAlignment codonAlignment = dataInput.get();
        int stateCount = codonAlignment.getDataType().getStateCount(); // 64
        assert stateCount == 64;

        // 0 - 63, ignore lowerValueInput upperValueInput
        m_fLower = 0;
        m_fUpper = stateCount-1; // not deal unknown codon

        // used to adjust Nr
        internalNodeCount = codonAlignment.getTaxonCount() - 1;
        assert internalNodeCount > 1;
        // L = num of codons, overwrite in CodonAlignment /= 3
        minorDimension = codonAlignment.getSiteCount();

        // internal nodes = n - 1, length = (n-1)*L
        this.values = new Integer[internalNodeCount * minorDimension];
        this.storedValues = values.clone();

        m_bIsDirty = new boolean[values.length];

    }

    /**
     * get an internal node states.
     * @param nodeNr the node index = <code>nodeNr - internalNodeCount - 1</code><br>
     *               Leaf nodes are number 0 to <code>leafnodes-1</code>;
     *               Internal nodes are numbered  <code>leafnodes</code> up to <code>nodes-1</code>;
     *               The root node is always numbered <code>nodes-1</code>.
     * @return the codon states of the internal node sequence.
     */
    public int[] getNrStates(final int nodeNr) {
        assert nodeNr > internalNodeCount; // internalNodeCount = getTaxonCount() - 1;
        int rowIndex = nodeNr - internalNodeCount - 1; // convert nodeNr into 2d matrix row index
        int[] row = new int[minorDimension];
        // matrix[i,j] = values[i * minorDimension + j]
        System.arraycopy(values, rowIndex * minorDimension, row, 0, row.length);
        return row;
    }

    /**
     * get the sites at a site of all internal nodes.
     * @param codonNr the codon site index.
     * @return
     */
    public int[] getSites(final int codonNr) {
        assert codonNr < minorDimension;
        int[] col = new int[internalNodeCount];
        for (int i = 0; i < internalNodeCount; i++)
            col[i] = values[i * minorDimension + codonNr];
        return col;
    }

    /**
     * get a codon state from the site at the internal node.
     * <code>matrix[i,j] = values[i * minorDimension + j]</code>
     *
     * @param nodeNr the node index <code>i = nodeNr - internalNodeCount - 1</code><br>
     *               Leaf nodes are number 0 to <code>leafnodes-1</code>;
     *               Internal nodes are numbered  <code>leafnodes</code> up to <code>nodes-1</code>;
     *               The root node is always numbered <code>nodes-1</code>.
     * @param codonNr the site index.
     * @return
     */
    public int getASite(final int nodeNr, final int codonNr) {
        assert nodeNr > internalNodeCount; // internalNodeCount = getTaxonCount() - 1;
        int rowIndex = nodeNr - internalNodeCount - 1; // convert nodeNr into 2d matrix row index
        assert codonNr < minorDimension;
        return values[rowIndex * minorDimension + codonNr];
    }


    /**
     * set a codon state to the site at the internal node.
     * <code>matrix[i,j] = values[i * minorDimension + j]</code>
     *
     * @param nodeNr the node index <code>i = nodeNr - internalNodeCount - 1</code><br>
     *               Leaf nodes are number 0 to <code>leafnodes-1</code>;
     *               Internal nodes are numbered  <code>leafnodes</code> up to <code>nodes-1</code>;
     *               The root node is always numbered <code>nodes-1</code>.
     * @param codonNr the site index.
     */
    public void setASite(final int nodeNr, final int codonNr, final int value) {
        assert nodeNr > internalNodeCount; // internalNodeCount = getTaxonCount() - 1;
        int rowIndex = nodeNr - internalNodeCount - 1; // convert nodeNr into 2d matrix row index
        assert codonNr < minorDimension;

        setValue(rowIndex * minorDimension + codonNr, value);
    }


    @Override
    public void log(long sampleNr, PrintStream out) {
        super.log(sampleNr, out);
    }
}