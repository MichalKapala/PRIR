#ifndef MINIMUM_H
#define MINIMUM_H

#include <memory>

#include "Function.h"
#include "Minimization.h"

class SimpleMinimization : public Minimization
{
private:
    void generateRandomPosition();

public:
    void find(double dr_ini, double dr_fin, int idleStepsLimit);
    SimpleMinimization(Function *f, double timeLimit);
    ~SimpleMinimization();

private:
    std::vector<std::unique_ptr<drand48_data>> randBufferV;
};

#endif
