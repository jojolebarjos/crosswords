clear

fileID = fopen('unreducedData.txt','r');
line = fgetl(fileID);
froms = zeros(1,1);
tos = zeros(1,1);
values = zeros(1,1);
indedg = 1;
indval = 1;
while(line ~= -1)
    if(indedg > 5000)
        break;
    end
    % get the froms
    ind = 2;
    fromStr = '';
    while(line(ind) ~= ',')
        fromStr = strcat(fromStr,line(ind));
        ind = ind + 1;
    end
    from = str2num(fromStr);
    
    if(from <= 0)
        fprintf('from leq zero: from-to:%d %d',from,to)
        pause
    end
    
    ind = ind + 2;
    
    % get the tos
    while(1);
        toStr = '';
        while(line(ind) ~= ',' && line(ind) ~= ']')
            toStr = strcat(toStr,line(ind));
            ind = ind + 1;
        end
        to = str2num(toStr);                        
        froms(indedg) = from;
        tos(indedg) = to;
        
        indedg = indedg + 1;                        
        if(line(ind) == ']')
            break;
        end
        ind = ind + 1;
    end
    
    ind = ind + 3;
    
    % get the values   
    while(1);
        valStr = '';
        while(line(ind) ~= ',' && line(ind) ~= ']')
            valStr = strcat(valStr,line(ind));
            ind = ind + 1;
        end
        val = str2num(valStr);        
        values(indval) = val;
        indval = indval + 1;
        if(line(ind) == ']')
            break;
        end
        ind = ind + 1;
    end
    
    line = fgetl(fileID);
end

% clean zero entries in the index vectors
zeroFroms = find(froms <= 0);
zeroTos = find(tos <= 0);
zeroEntries = union(zeroFroms,zeroTos);
len = length(froms);
indToBeIncluded = setdiff(1:len,zeroEntries);

froms = froms(indToBeIncluded);
tos = tos(indToBeIncluded);
values = values(indToBeIncluded);

processedUnreducedData = sparse(froms,tos,values);
save('processedUnreducedData','processedUnreducedData');
clear
