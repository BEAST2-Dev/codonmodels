# pipeline step 1
# simulate tree

library(ape)

WD="~/WorkSpace/codonsubstmodels/perftest"
setwd(WD)

# c(32, 128, 512)
n.taxa = 32
DIR=paste0("T",n.taxa)
if (!dir.exists(DIR)) {
  dir.create(DIR)
}
# working dir
setwd(file.path(WD, DIR))

# omega values <1, =1, and >1 indicating purifying selection, neutral evolution, and diversifying selection
omega=10
# coal or yulelam10
tree.prior = paste0("yuleOmega",omega)

if (!dir.exists(tree.prior)) {
  dir.create(tree.prior)
}

if (tree.prior == "coal") {
  # simulate coalescent tree
  tre = rcoal(n.taxa)
  # original
  write.tree(tre, file=paste0("t",n.taxa,tree.prior,".bak.txt"))
  # rescale all branches to make tree height about 0.5
  target.height = 0.5
  require(phytools)
  root.height = max(nodeHeights(tre)[,2])
  scale = target.height / root.height 
  tre$edge.length<- tre$edge.length * scale
  #max(nodeHeights(tre)[,2])
  
} else {
  require(TreeSim)
  # lambda 10
  tre <- sim.bd.taxa(n=n.taxa, numbsim=1, lambda=10, mu=0)[[1]]

}

# check branch lengths, cannot be too short
stopifnot(all(tre$edge.length>0))

# plot tree
#pdf(paste0("t",n.taxa,".pdf"))
plot(tre)
edgelabels(round(tre$edge.length,4), col="black", font=2)
#nodelabels()
#tiplabels(adj = 2)
#dev.off()

# save tree
write.tree(tre, file=paste0("t",n.taxa,tree.prior,".txt"))

# replace TAXA and tree in the template
tree.txt <- readLines(paste0("t",n.taxa,tree.prior,".txt"))

### this is for testing longer branches
# tree.txt <- paste0("t",n.taxa,"yulelam10.txt")
# tre <- read.tree(text = tree.txt)
# scale = 20
# tre$edge.length<- tre$edge.length * scale
# plot(tre)
# edgelabels(round(tre$edge.length,4), col="black", font=2)
# tree.txt <- write.tree(tre, file="")
# tree.prior = paste0("yuleBrLen", scale)
# if (!dir.exists(tree.prior)) {
#   dir.create(tree.prior)
# }
### make sure tree.prior changed (define working dir) 

# read evolver config
template <- readLines(file.path(WD, "CodonTemplate.dat"))

# change working dir
setwd(file.path(WD, DIR, tree.prior))

evolver <- gsub(pattern = "TAXA", replace = toString(n.taxa), x = template)
evolver <- gsub(pattern = "TREE", replace = tree.txt, x = evolver)
# evolver <- gsub(pattern = "(0\\.08)(.*omega)", replace = paste0(omega,"\\2"), x = evolver)
writeLines(evolver, con=paste0("t",n.taxa,".evolver.dat"))

# /Applications/paml4.8/bin/evolver 6 t8.evolver.dat > t8.out.txt
CMD="/Applications/paml4.8/bin/evolver 6"
try(system(paste(CMD, paste0("t",n.taxa,".evolver.dat"), ">", paste0("t",n.taxa,".out.txt")), intern = TRUE))

# working dir
setwd(file.path(WD, DIR))


# load XML

# load codons

##########

#tre.txt <- readLines(paste0("t",n.taxa,tree.prior,".txt"))
#tre <- read.tree(text = tre.txt)

# generate from MASTER YuleTree.xml
#tree.txt <- readLines(paste0("t",n.taxa+1,"YuleLambda10-0height.txt"))
# add prefix to taxon name
#tree.txt <- gsub("([0-9]+:)", "t\\1", x = tree.txt)



