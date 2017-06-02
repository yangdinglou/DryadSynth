import java.util.*;
import com.microsoft.z3.*;

public class Cegis {
	
	private Context ctx;
	private SygusExtractor extractor;
	private int numVar;
	private int numV;
	private int numFunc;
	private BoolExpr finalConstraint;

	public IntExpr[] var;
	public ArithExpr[] functions;
	public HashSet<IntExpr[]> counterExamples;

	public Cegis(Context ctx, SygusExtractor extractor) {
		this.ctx = ctx;
		this.extractor = extractor;

		ArrayList<Integer> argsNumList = new ArrayList<Integer>();
		for(FuncDecl func : extractor.requests.values()) {
			Integer argsNum = func.getDomain().length;
			argsNumList.add(argsNum);
		}

		this.numV = Collections.max(argsNumList);
		this.numVar = extractor.vars.size();
		this.numFunc = extractor.requests.size();
		this.finalConstraint = extractor.finalConstraint;

		var = new IntExpr[numVar];
		functions = new ArithExpr[numFunc];
		counterExamples = new HashSet<IntExpr[]>();

		init();
		//addRandomInitialExamples();
		addSimpleExamples();
	}

	public void init() {
		for (int i = 0; i < numVar; i++) {
			var[i] = ctx.mkIntConst("var" + i);
		}

		for (int i = 0; i < numFunc; i++) {
			functions[i] = ctx.mkInt(0);
		}
	}

	public void addRandomInitialExamples() {

		//int numExamples = (int)Math.pow(4, numVar) + 1;
		int numExamples = (int)Math.pow(numVar, 3) + 1;
		//int numExamples = (int)Math.pow(2, numVar) + 1;
		//int numExamples = 90;

		for (int i = 0; i < numExamples; i++) {
			//IntExpr[] randomExample = new IntExpr[numVar];
			IntExpr[] randomExample = new IntExpr[numVar];
			for (int j = 0; j < numVar; j++) {
				Random rand = new Random();
				int n;
				int randomFlag = rand.nextInt(10);
				if (randomFlag%2 == 0) {
					n = rand.nextInt(10);
				} else {
					n = -rand.nextInt(10);

				}
				randomExample[j] = ctx.mkInt(n);
			}
			counterExamples.add(randomExample);
		}
		
	}

	public IntExpr[][] addSimpleExamplesRecursive(int nv) {
		IntExpr[][] examples = new IntExpr[(int)Math.pow(3, nv)][numVar];

		if (nv == 0) return examples;

		else {
			IntExpr[][] smaller_examples = addSimpleExamplesRecursive(nv-1);

			for (int j = 0; j < (int)Math.pow(3, nv-1); j++) {
				examples[j] = smaller_examples[j];
				examples[j][nv-1] = ctx.mkInt(-1);
				examples[(int)Math.pow(3, nv-1) + j] = smaller_examples[j];
				examples[(int)Math.pow(3, nv-1) + j][nv-1] = ctx.mkInt(0);
				examples[(int)Math.pow(3, nv-1) * 2 + j] = smaller_examples[j];
				examples[(int)Math.pow(3, nv-1) * 2 + j][nv-1] = ctx.mkInt(1);
			}

			return examples;
		
		}

	}

	public void addSimpleExamples() {
		IntExpr[][] examples = addSimpleExamplesRecursive(numVar);
		for (int j = 0; j < examples.length; j++) {
			counterExamples.add(examples[j]);
		}
	}

	public void cegis() {
		
		boolean flag = true;
		int heightBound = 1;
		int condBound = 1;
		int condBoundInc = 1;

		int k = 0;	//number of iterations

		//print out initial examples
		for (IntExpr[] example : counterExamples) {
			for (int i = 0; i < numVar; i++) {
				System.out.println("initial example: var" + i + " : " + example[i]);
			}
			System.out.println();
		}

		while(flag) {

			k = k + 1;

			System.out.println("Start verifying............");
			Verifier testVerifier = new Verifier(ctx, numVar, numV, numFunc, var, extractor);

			Status v = testVerifier.verify(functions);

			if (v == Status.UNSATISFIABLE) {
					System.out.println("Done! Synthesized function(s): ");
					for (int i = 0; i < numFunc; i++) {
						System.out.println("f" + i + " : " + functions[i]);
					}
					flag = false;
					
				} else if (v == Status.UNKNOWN) {
					System.out.println("Verifier Error : Unknown!");
					flag = false;

				} else if (v == Status.SATISFIABLE) {
					
					System.out.println(testVerifier.s.getModel());	//for test only
					VerifierDecoder decoder = new VerifierDecoder(ctx, testVerifier.s.getModel(), numVar, var);

					IntExpr[] cntrExmp = decoder.decode();
					counterExamples.add(cntrExmp);
					//print out for debug
					System.out.println("Verifier satisfiable! Counter example(s): ");
					for (IntExpr[] params : counterExamples) {
						for (int i = 0; i < numVar; i++) {
							System.out.println("var" + i + " : " + params[i]);
						}
						System.out.println();
					}

					//for test only
					//if (k >= 10) {
					//	break;
					//}

					boolean unsat = true;

					while(unsat) {

						Synthesizer testSynthesizer = new Synthesizer(ctx, numVar, numV, numFunc, counterExamples, heightBound, extractor);
						//print out for debug
						System.out.println("Start synthesizing............");

						Status synth = testSynthesizer.synthesis(condBound);
						//print out for debug
						System.out.println("Synthesis Done!");

						if (synth == Status.UNSATISFIABLE) {
							System.out.println("Synthesizer : Unsatisfiable!");
							condBound = (int)Math.pow(2, condBoundInc);
							System.out.println("Synthesizer : Increase coefficient bound to " + condBound);
							condBoundInc = condBoundInc + 1;
							if (condBoundInc > 6) {
								heightBound = heightBound + 1;
								System.out.println("Synthesizer : Increase height bound to " + heightBound);
								condBound = 1;
								condBoundInc = 1;
							}
							//flag = false;
						} else if (synth == Status.UNKNOWN) {
							System.out.println("Synthesizer Error : Unknown!");
							flag = false;
							unsat = false;
						} else if (synth == Status.SATISFIABLE) {

							unsat = false;
							//flag = false;	//for test only

							//System.out.println(testSynthesizer.s.getModel());	//for test only
							SynthDecoder synthDecoder = new SynthDecoder(ctx, testSynthesizer.s.getModel(), testSynthesizer.e.getCoefficients(), testSynthesizer.bound, numV, numFunc);
							//print out for debug
							System.out.println("Start decoding synthesizer output............");
							functions = synthDecoder.generateFunction(var);
							//print out for debug
							System.out.println("Synthesizer output decode done!");
							//print out for debug
							for (int i = 0; i < numFunc; i++) {
								System.out.println("f" + i + " : " + functions[i]);
							}
							System.out.println();
						}
					}

				}

			System.out.println("Iteration : " + k);
			System.out.println();

		}
		

	}

}