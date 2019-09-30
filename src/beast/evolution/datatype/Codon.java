/*
 * Codons.java
 *
 * Copyright (c) 2002-2015 Alexei Drummond, Andrew Rambaut and Marc Suchard
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package beast.evolution.datatype;

import beast.core.Input;
import beast.core.util.Log;
import beast.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements DataType for codon,
 * where codon states are also the indices of CODON_TRIPLETS,
 * and also the same character index from the string of
 * the currently used genetic code table.
 * <p/>
 * Codon have tree different representations:
 * State numbers (codon states) - 0-63 + 64, 65 as unknown and gap
 * State chars - the above converted into chars, starting at 'A'
 * and '?' + '-' for unknown and gap
 * Strings or chars of three nucleotide characters
 *
 * Modified from BEAST 1 Codon.
 *
 * @author Andrew Rambaut
 * @author Alexei Drummond
 * @author Walter Xie
 */
public class Codon extends DataType.Base {

    final public Input<String> geneticCodeInput = new Input<>("geneticCode",
            "The rule to define how sequences of nucleotide triplets, " +
                    "called codons, specify which amino acid will be added next during protein synthesis.",
            GeneticCode.GENETIC_CODE_NAMES[GeneticCode.UNIVERSAL_ID], Input.Validate.REQUIRED);


//    public static final int UNKNOWN_STATE = 64;
//    public static final int GAP_STATE = 65;

    // NOT include ?, have to replace all ? to - in sequence
    // = codeMap, state > 64 contains ambiguous nucleotides
    public static final String[] CODON_TRIPLETS = {
            "AAA", "AAC", "AAG", "AAT", "ACA", "ACC", "ACG", "ACT",
            "AGA", "AGC", "AGG", "AGT", "ATA", "ATC", "ATG", "ATT", // 16
            "CAA", "CAC", "CAG", "CAT", "CCA", "CCC", "CCG", "CCT",
            "CGA", "CGC", "CGG", "CGT", "CTA", "CTC", "CTG", "CTT", // 32
            "GAA", "GAC", "GAG", "GAT", "GCA", "GCC", "GCG", "GCT",
            "GGA", "GGC", "GGG", "GGT", "GTA", "GTC", "GTG", "GTT", // 48
            "TAA", "TAC", "TAG", "TAT", "TCA", "TCC", "TCG", "TCT",
            "TGA", "TGC", "TGG", "TGT", "TTA", "TTC", "TTG", "TTT", // 64 here
            "AA-", "AC-", "AG-", "AT-", "CA-", "CC-", "CG-", "CT-",
            "GA-", "GC-", "GG-", "GT-", "TA-", "TC-", "TG-", "TT-", // 80
            "A-A", "A-C", "A-G", "A-T", "C-A", "C-C", "C-G", "C-T",
            "G-A", "G-C", "G-G", "G-T", "T-A", "T-C", "T-G", "T-T", // 96
            "-AA", "-AC", "-AG", "-AT", "-CA", "-CC", "-CG", "-CT",
            "-GA", "-GC", "-GG", "-GT", "-TA", "-TC", "-TG", "-TT", // 112
            "A--", "C--", "G--", "T--", "-A-", "-C-", "-G-", "-T-", // 120
            "--A", "--C", "--G", "--T", "---" // 125
    }; // "---" must be the last


//    public int[] getNoAmbiguousStatesOf(int state) {
//        if (state < stateCount) {
//            return new int[]{state};
//        } else {
//            String noAmbiguous =
//TODO
//
//        }
//    }


    /**
     * This character represents the amino acid equivalent of a stop codon to cater for
     * situations arising from converting coding DNA to an amino acid sequence.
     */
    public static final char STOP_CHARACTER = '*';
//    public static final int STOP_STATE = 23;
    public final static String CODON = "codon";

    @Override
    public void initAndValidate() {
        GeneticCode geneticCode = GeneticCode.findByName(geneticCodeInput.get());
        setGeneticCode(geneticCode);
    }

    // ambiguous states > 63
    public int getStateCountAmbiguous(){
        return CODON_TRIPLETS.length;
    }


    protected GeneticCode geneticCode;

    // this constructor used by xml not java
    public Codon() {
        this(GeneticCode.UNIVERSAL);
    }

    public Codon(GeneticCode geneticCode) {
        setGeneticCode(geneticCode);
    }

    //stateMap saves the codon states 0-63, but the states of stop codon are saved in the end.
    protected int[] stateMap;

    /**
     * Codon states are the triplets index in CODON_TRIPLETS,
     * and are also the character index from the string of
     * the currently used genetic code table.
     * codeMap is the concatenated string of of triplets in CODON_TRIPLETS.
     *
     *
     * This method enable CodonAlignment to choose the correct GeneticCode,
     * especially after calling the default constructor using UNIVERSAL.
     */
    public void setGeneticCode(GeneticCode geneticCode) {
        this.geneticCode = geneticCode;

        // triplets
        codeLength = 3;
        // fix stateCount to 64 (no ambiguous)
        // the codon states are used as array indices,
        // removing stop codon will break code in frequency and subst model
        stateCount = 64;
        assert stateCount == geneticCode.getCodeTableLength();
        // use triplets index in CODON_TRIPLETS as codon states
        // Universal: 0-60 triplets, 61-63 *,
        codeMap = StringUtils.concatenateToString(CODON_TRIPLETS);

        assert codeMap.length()/codeLength == getStateCountAmbiguous();

        // getStateCountAmbiguous() == 125, last is ---
        assert getStateCountAmbiguous() == 125;
        mapCodeToStateSet = new int[getStateCountAmbiguous()][];
        // number of stop codon
        int nStopCodon = geneticCode.getStopCodonCount();
        List<Integer> allNonStopCodon = new ArrayList<>(); //new int[stateCount-nStopCodon];
        for (int i = 0; i < 64; i++) {
            mapCodeToStateSet[i] = new int[]{i};
            // fill in non stop codon
            if (!geneticCode.isStopCodon(i)) {
                allNonStopCodon.add(i);
            }
        }
        assert allNonStopCodon.size() == stateCount - nStopCodon;
        // --- represented by all possible states
        mapCodeToStateSet[124] = allNonStopCodon.stream().mapToInt(x->x).toArray();

        // loop exclude ---
        for (int i = 64; i < 124; i++) {
            List<Integer> states = new ArrayList<>();

            if (i < 112) {
                int state; // codon state [0, 63]
                // one ambiguous
                for (int n = 0; n < 4; n++) {
                    if (i < 80) {
                        // i.e. "AAA", "AAC", "AAG", "AAT"
                        state = (i - 64) * 4 + n;
                    } else if (i < 96) {
                        // i.e. "AAA", "ACA", "AGA", "ATA"
                        state = (i - 80) % 4 + n * 4 + (int) Math.ceil((i - 80) / 4) * 16;
                    } else { // i < 112
                        // i.e. "AAA", "CAA", "GAA", "TAA"
                        state = i - 96 + n * 16;
                    }
                    assert state >= 0 && state < 64;
                    // no stop codon
                    if (!geneticCode.isStopCodon(state))
                        states.add(state);
                } // end n loop

            } else {
                // two ambiguous
                if (i < 116) { // A--
                    states = Stream.iterate((i-112)*16, n -> n + 1)
                            .limit(16).collect(Collectors.toList());
                } else if (i < 120) { // -A-
                    for (int n = 0; n < 16; n++)
                        states.add(n + (int) Math.ceil((n / 4)) * 12 + (i-116) * 4);
                } else { // --A
                    states = Stream.iterate((i-120), n -> n + 4)
                            .limit(16).collect(Collectors.toList());
                }
                for (int state : states)
                    assert state >= 0 && state < 64;
                // safe remove stop codon
                states.removeIf(geneticCode::isStopCodon);
            }

            mapCodeToStateSet[i] = states.stream().mapToInt(x->x).toArray();
//            printAmbiguousState(i);
        } // end i loop


        // TODO
        // array index is same as frequency array index, but stop codon in the end,
        // value is codon states, no ambiguous
        stateMap = new int[geneticCode.getCodeTableLength()];
        int j = 0;
        int k = geneticCode.getCodeTableLength()- nStopCodon;
        // put stop codon in the last, i.e. 63-65
        for (int i = 0; i < geneticCode.getCodeTableLength(); i++) {
            if (!geneticCode.isStopCodon(i)) {
                stateMap[j] = i;
                j++;
            } else {
                stateMap[k] =i;
                k++;
            }
        }

        // 1st stop codon state
        k = geneticCode.getCodeTableLength()- nStopCodon;
        assert geneticCode.isStopCodon(stateMap[k]);

    }

    // print ambiguous state [64,124] map code to non-ambiguous states
    public void printAmbiguousState(int state) {
        Log.info.print(state + " " + getTriplet(state) + " : " + Arrays.toString(mapCodeToStateSet[state]) + " ");
        Arrays.stream(mapCodeToStateSet[state]).mapToObj(this::getTriplet).
                map(triplet -> triplet + " ").forEach(Log.info::print);
        Log.info.println();
    }

    public int getCodonState(int stateMapIndex) {
        return stateMap[stateMapIndex];
    }


    @Override
    public String getTypeDescription() {
        return CODON;
    }

    //=========== Nucleotide ==========
    /**
     * index in {@link Nucleotide#codeMap codeMap}
     */
    public static final int NUC_GAP_STATE = 17; // -
    public static final int NUC_MISSING_STATE = 18; // ?

//    /**
//     * Get state indexed by {@link Codon#codeMap codeMap}
//     * corresponding to a nucleotide triplet
//     *
//     * @param nuc1 the codon triplet as chars
//     * @param nuc2 the codon triplet as chars
//     * @param nuc3 the codon triplet as chars
//     * @return state
//     */
//    public final int getCodonState(char nuc1, char nuc2, char nuc3) {
//        char ns1 = geneticCode.getNucleotideChar(nuc1);
//        char ns2 = geneticCode.getNucleotideChar(nuc2);
//        char ns3 = geneticCode.getNucleotideChar(nuc3);
//
//        if (ns1 == NUC_GAP_STATE || ns2 == NUC_GAP_STATE ||
//                ns3 == NUC_GAP_STATE)
//            return GAP_STATE;
//        if (isAmbiguousCode(ns1) || isAmbiguousCode(ns2) || isAmbiguousCode(ns3))
//            return UNKNOWN_STATE;
//
//        return stringToEncoding("" + nuc1 + nuc2 + nuc3).get(0);
//    }

    /**
     * Get codon state indexed by {@link Codon#codeMap codeMap}
     * corresponding to a nucleotide triplet
     *
     * @param ns1 the codon triplet as states
     * @param ns2 the codon triplet as states
     * @param ns3 the codon triplet as states
     * @return codon state, same as {@link Codon#CODON_TRIPLETS CODON_TRIPLETS} index
     */
    public final int getCodonState(int ns1, int ns2, int ns3) {
//        if (ns1 == NUC_GAP_STATE || ns2 == NUC_GAP_STATE ||
//                ns3 == NUC_GAP_STATE)
//            return GAP_STATE;
//        if (isAmbiguousCode(ns1) || isAmbiguousCode(ns2) || isAmbiguousCode(ns3))
//            return UNKNOWN_STATE;

//        int canonicalState = (ns1 * 16) + (ns2 * 4) + ns3; // cannot use BEAST1 nice design
        char nuc1 = geneticCode.getNucleotideChar(ns1);
        char nuc2 = geneticCode.getNucleotideChar(ns2);
        char nuc3 = geneticCode.getNucleotideChar(ns3);

        return stringToEncoding("" + nuc1 + nuc2 + nuc3).get(0);
    }

    /**
     * Get triplet string corresponding to a given state
     * indexed by {@link Codon#codeMap codeMap}
     *
     * @param state codon state, same as {@link Codon#CODON_TRIPLETS CODON_TRIPLETS} index
     * @return the corresponding triplet string
     */
    public final String getTriplet(int state) {
        String triplet;
        try {
            triplet = encodingToString(new int[]{state}); // states from codeMap
        } catch (IndexOutOfBoundsException e) {
            Log.err.println("Invalid triplet state : " + state + ", should be between 0 and 65 !");
            throw new RuntimeException(e.getLocalizedMessage());
        }
        return triplet;
    }

    /**
     * Get an array of three nucleotide states making this codon state,
     * where nucleotide states are indexed by {@link Nucleotide#codeMap codeMap}.
     *
     * @param state codon state, same as {@link Codon#CODON_TRIPLETS CODON_TRIPLETS} index
     * @return the corresponding 3 Nucleotide state referred to
     *         {@link GeneticCode#getNucleotideState getNucleotideState}
     */
    public final int[] getTripletNucStates(int state) {
        int[] triplet = new int[3];

        triplet[0] = geneticCode.getNucleotideState(getTriplet(state).charAt(0));
        triplet[1] = geneticCode.getNucleotideState(getTriplet(state).charAt(1));
        triplet[2] = geneticCode.getNucleotideState(getTriplet(state).charAt(2));

        return triplet;
    }

    //=========== Amino Acid ==========

    /**
     * Amino Acid character of a state
     * @param state
     * @return  Amino Acid
    @Deprecated
    public final char getChar(int state) {
//        throw new IllegalArgumentException("Codon datatype cannot be expressed as char");
        return geneticCode.getAminoAcid(state);
    }*/

    /**
     * Same function of {@link DataType#encodingToString(int[])} encodingToString(int[])},
     * but return Amino Acid string.
     * @param states codon state, same as {@link Codon#CODON_TRIPLETS CODON_TRIPLETS} index
     * @return Amino Acid string
     */
    public String stateToAminoAcid(int[] states) {
        StringBuilder strB = new StringBuilder();
            // produce a comma separated string of integers
        for (int state : states)
            strB.append(geneticCode.getAminoAcid(state));
        return strB.toString();
    }

    /**
     * @return the genetic code
     */
    public GeneticCode getGeneticCode() {
        return geneticCode;
    }


}
