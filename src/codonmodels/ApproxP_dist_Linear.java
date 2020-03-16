package codonmodels;

import beast.core.Input;
import beast.core.parameter.RealParameter;
import beast.evolution.alignment.Alignment;
import beast.evolution.alignment.CodonAlignment;
import beast.evolution.alignment.Sequence;
import beast.util.RandomUtils;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Create points to plot P(dist) by fixing other parameters.
 * dist = time * rate
 *
 * @author Walter Xie
 */
public class ApproxP_dist_Linear extends CodonSubstitutionModel {
    final public Input<CodonSubstitutionModel> substModelInput = new Input<>("substModel",
            "substitution model we want to approximate", Input.Validate.REQUIRED);

    final public Input<String> filePathInput = new Input<>("file",
            "file path to save all data points used in the linear approximation");

    CodonSubstitutionModel codonSubstModel; // such as MO

    double MaxDistance = 100;
    double err = 5E-1;
    double step = 0.01;
    int multiply = 10;

    double[][] p_d_; // 1st time interval, 2nd flattened P(t) index
    double[] intervals;

    double[] prob;
    double[] iexp;

    public ApproxP_dist_Linear() {
        frequenciesInput.setRule(Input.Validate.OPTIONAL);
    }

    @Override
    public void initAndValidate() {
        codonSubstModel = substModelInput.get();
        frequenciesInput.setValue(codonSubstModel.frequenciesInput.get(), this);
        super.initAndValidate();
        eigenDecomposition = null;

        System.out.println("\nLinear approximate " + codonSubstModel.getID());
        if (codonSubstModel instanceof M0Model)
            System.out.println("\nomega = " + ((M0Model) codonSubstModel).omegaInput.get() +
                    ", kappa = " + ((M0Model) codonSubstModel).kappaInput.get());

        // for caching
        prob = new double[nrOfStates * nrOfStates];
        iexp = new double[nrOfStates * nrOfStates];

        double[] freq = getFrequencies();
        System.out.println("\nfreqs = \n" + Arrays.toString(freq) + "\n");
        for (int i = 0; i < freq.length; i++) {
            if (freq[i] == 0)
                throw new IllegalArgumentException("Frequency at codon index " + i + " is 0 !");
            if (freq[i] < 1E-5)
                System.err.println("Small frequency (< 1E-5) found at codon index " + i);
        }

        createIntervals(freq);

        assert p_d_[0].length == nrOfStates * nrOfStates;

        if (filePathInput.get() != null) { // "p_d_.txt"
            Path path = Paths.get(filePathInput.get());
            try {
                writeP_d_(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Approximate P(t) by caching the list of P(t) matrices in time intervals.
     */
    public void getTransiProbs(double startTime, double endTime, double rate, double[] iexp, double[] matrix) {
        // distance = (startTime - endTime) * mean branch rate * rate for a site category.
        double distance = (startTime - endTime) * rate;
        // > biggest distance
        int i = intervals.length-1;
        if (distance == intervals[i])
            System.arraycopy(p_d_[i], 0 , matrix, 0, matrix.length);

        // intervals[i-1] <= distance <= intervals[i]
        i = RandomUtils.binarySearchSampling(intervals, distance);
        if (distance == intervals[i]) {
            System.arraycopy(p_d_[i], 0 , matrix, 0, matrix.length);
        } else { // approximation

            for (int j = 0; j < p_d_[i].length; j++) {
                matrix[j] = LinearApproximation(distance, intervals[i - 1], intervals[i],
                        p_d_[i-1][j], p_d_[i][j]);
            }
        }

    }

    // return linear approximation, distance = (startTime - endTime) * rate,
    private double LinearApproximation(double distance, double x1, double x2, double y1, double y2) {
        // y = (x-x1) * (y2-y1) / (x2-x1) + y1, where x1 < x < x2, y1 < y < y2
        return (distance - x1) * (y2 - y1) / (x2 - x1) + y1;
    }


    public static void main(final String[] args) {

        // Not use sequence, but have to init
        Sequence s1 = new Sequence("aaa", "AAA");
        Sequence s2 = new Sequence("aac", "AAA");

        Alignment data = new Alignment();
        data.initByName("sequence", s1, "sequence", s2, "dataType", "nucleotide");
        // create Codon Alignment
        CodonAlignment codonAlignment = new CodonAlignment();
        codonAlignment.initByName("data", data, "dataType", "codon",
                "geneticCode", "vertebrateMitochondrial");

        // equal
        CodonFrequencies codonFreq = new CodonFrequencies();
        codonFreq.initByName("pi", "equal", "data", codonAlignment, "verbose", true);

        RealParameter omegaPara = new RealParameter("0.08");
        RealParameter kappaPara = new RealParameter("15");

        M0Model m0Model = new M0Model();
        m0Model.initByName("omega", omegaPara, "kappa", kappaPara,
                "frequencies", codonFreq, "verbose", true);

        ApproxP_dist_Linear pd = new ApproxP_dist_Linear();
//        pd.initByName("substModel", m0Model, "file", "p_d_1.txt");
        pd.initByName("substModel", m0Model);
        pd.printP_d_();

    }

    public void createIntervals(double[] freq){
        List<Double> intervalList = new ArrayList<>();
        List<double[]> p_d_List = new ArrayList<>();

        double rate = 1.0;
        double t=0,d = 0;
        int k;
        double[] tmp = new double[prob.length];
        double[] tru = new double[prob.length];
        double x1,x2;
        double[] y1Array;

        while (d < MaxDistance) {
            d = Math.round(t * rate * 100000.0) / 100000.0;
            // eigen decomp to get points, startTime > endTime
            codonSubstModel.getTransiProbs(d, iexp, prob);
            // approx value test in mid point
            k = intervalList.size() - 1;

            if (k > 2) { // start with 3 points
                x1 = intervalList.get(k);
                x2 = d;
                y1Array = p_d_List.get(k);

                for (int j = 0; j < prob.length; j++)
                    tmp[j] = LinearApproximation(x2-x1, x1, x2, y1Array[j], prob[j]);
                codonSubstModel.getTransiProbs(x2-x1, iexp, tru);

                if (largeErr(tmp, tru))
                    // keep previous last element, work on this newly added element
                    add(intervalList, p_d_List, d, prob);
                else // replace last
                    set(intervalList, p_d_List, d, k, prob);

            } else {
                add(intervalList, p_d_List, d, prob);
            }

            if (t == 0) {
                t += 1e-4;
            } else if (t < step) {
                t = step;
            } else {
                t += step;
            }
        }

        intervals = intervalList.stream().mapToDouble(i -> i).toArray();
        p_d_ = getP_dist_(p_d_List);

    }

    private void add(List<Double> intervalList, List<double[]> p_d_List, double d, double[] prob) {
        intervalList.add(d);
        double[] tmp = new double[prob.length];
        System.arraycopy(prob, 0, tmp, 0, prob.length);
        p_d_List.add(tmp);
    }

    private void set(List<Double> intervalList, List<double[]> p_d_List, double d, int k, double[] prob) {
//        d = Math.round(d * 100000.0) / 100000.0;
        intervalList.set(k, d);
        double[] tmp = new double[prob.length];
        System.arraycopy(prob, 0, tmp, 0, prob.length);
        p_d_List.set(k, tmp);
    }

    private boolean largeErr(double[] val, double[] trueVal) {

        for (int i = 0; i < val.length; i++) {
            double error = Math.abs(val[i] - trueVal[i]) / trueVal[i];
            if (error > err)
                return true;
        }
        return false;
    }


    // 1st[] is time, 2nd[] is flattened array of P(t)
    public double[][] getP_dist_(List<double[]> ptList) {
        double[][] p_d_ = new double[ptList.size()][];

        for (int i = 0; i < ptList.size(); i++) {
            double[] probs = ptList.get(i);
            p_d_[i] = new double[probs.length];
            for (int j = 0; j < probs.length; j++) {
                p_d_[i][j] = probs[j];
            }
        }
        return p_d_;
    }


    public void writeP_d_(Path path) throws IOException {

        assert intervals.length == p_d_.length;

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (int i = 0; i < intervals.length; i++) {
                writer.write(intervals[i] + "\t");
                double[] probs = p_d_[i];
                for (double pr : probs) {
                    writer.write("\t" + pr);
                }
                writer.newLine();
            }
        }

    }

    public void printP_d_() {

        assert intervals.length == p_d_.length;

        for (int i = 0; i < 5; i++) {//intervals.length
            System.out.print(intervals[i] + "\t");
            double[] probs = p_d_[i];
            for (double pr : probs) {
                System.out.print("\t" + pr);
            }
            System.out.println();
        }
        System.out.println("\n");
        for (int i = intervals.length-5; i < intervals.length; i++) {//
            System.out.print(intervals[i] + "\t");
            double[] probs = p_d_[i];
            for (double pr : probs) {
                System.out.print("\t" + pr);
            }
            System.out.println();
        }

        System.out.println("\n" + intervals.length + " data points.");
    }


}

/**
 * Find max distance in x-axis when the P(d) closes to equilibrium freq.
 public double getMaxDistance(double[] freq) {
 double err = 1E-4;
 int d; // distance = time * rate
 for (d = 10; d < 1000; d+=10) {
 // intervals[0] > 0
 codonSubstModel.getTransiProbs(d, iexp, prob);

 boolean all = true;
 for (int i = 0; i < freq.length; i++) {
 if (prob[i]-freq[i] > err) // TODO check i->j in matrix
 all = false;
 }

 if (all)
 return d;
 }
 throw new IllegalArgumentException("Cannot find the max time !");
 }

 // intervals[0] > 0
 public void createTimeIntervals(double maxDistance){
 System.out.println("Max distance = " + maxDistance);

 int first = 3;
 int second = 30;
 int third = (int) maxDistance / 4;
 List<Double> intervalList = new ArrayList<>();

 int i = -1; // list index
 double end = -0.02;
 while (end < first) {
 i++;
 end += 0.02;
 end = Math.round(end*100) / 100.0;
 intervalList.add(end);
 }
 System.out.println("Create " + intervalList.size() + " intervals from " + intervalList.get(0) +
 " to " + intervalList.get(i));

 int j = i;
 while (end < second) {
 j++;
 end += 0.5;
 end = Math.round(end*10) / 10.0;
 intervalList.add(end);
 }
 System.out.println("Create " + (intervalList.size()-i+1) + " intervals from " + intervalList.get(i+1) +
 " to " + intervalList.get(j));

 int k = j;
 while (end < third) {
 k++;
 end += 2;
 end = Math.round(end);
 intervalList.add(end);
 }
 System.out.println("Create " + (intervalList.size()-j+1) + " intervals from " + intervalList.get(j+1) +
 " to " + intervalList.get(k));

 while (end < maxDistance) {
 end += 20;
 end = Math.round(end);
 intervalList.add(end);
 }
 System.out.println("Create last intervals " + (intervalList.size()-k+1) +
 " intervals from " + intervalList.get(k+1) + " to " + intervalList.get(intervalList.size()-1));

 this.intervals = intervalList.stream().mapToDouble(d -> d).toArray();
 }


 * Compute the exact value of P(t) at each time interval.

 public void computeTransiProbsByTime() {

 List<double[]> ptList = new ArrayList<>();

 for (int i = 0; i < intervals.length; i++) {
 // eigen decomp to get points
 // startTime > endTime, intervals[0] > 0
 codonSubstModel.getTransiProbs(intervals[i], iexp, prob);
 //            m0Model.getTransitionProbabilities(null, startTime, endTime, rate, prob);
 double[] tmp = new double[prob.length];
 System.arraycopy(prob, 0, tmp, 0, prob.length);
 ptList.add(tmp);

 }

 p_d_ = getP_dist_(ptList);
 }*/

//    public void getTransiProbsByOmega(double maxOmega, double maxTime, double rate) {
//        List<double[]> ptList = new ArrayList<>();
//
//        final double step = 0.01;
//        final double endTime = 0; // startTime > endTime
//        double startTime = endTime + step;
//
//        while (startTime < maxTime) {
//            // eigen decomp
//            codonSubstModel.getTransiProbs(startTime, endTime, rate, iexp, prob);
////            m0Model.getTransitionProbabilities(null, startTime, endTime, rate, prob);
//            double[] tmp = new double[prob.length];
//            System.arraycopy(prob, 0, tmp, 0, prob.length);
//            ptList.add(tmp);
//
//            startTime += step;
//
//        }
//
//    }
