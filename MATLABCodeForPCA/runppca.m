clear
processedUnreducedData = load('processedUnreducedData');
processedUnreducedData = processedUnreducedData.processedUnreducedData;
[pc,W,data_mean,xr,evals,percentVar]=ppca(processedUnreducedData,2);
save('ppca_results');
