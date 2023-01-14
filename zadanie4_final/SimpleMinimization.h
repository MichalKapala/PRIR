#ifndef MINIMUM_H
#define MINIMUM_H

#include "Function.h"
#include "Minimization.h"

#include <memory>

class SimpleMinimization : public Minimization {
private:
  void generateRandomPosition(double &xl, double &yl, double &zl,
                              drand48_data &buff);

public:
  void find(double dr_ini, double dr_fin, int idleStepsLimit);
  SimpleMinimization(Function *f, double timeLimit);
  ~SimpleMinimization();
};

#endif
