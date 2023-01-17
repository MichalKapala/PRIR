#include "SimpleMinimization.h"

#include <iostream>
#include <math.h>
#include <stdlib.h>
#include <sys/time.h>

#include <omp.h>

const double DR_SHRINK = 0.8;

using namespace std;

SimpleMinimization::SimpleMinimization(Function *f, double timeLimit)
    : Minimization(f, timeLimit) {

  // initalize values. Here we dont need to make it thread safe
  // No need of separate struct

  drand48_data buff;
  generateRandomPosition(x, y, z, buff);
  bestX = x;
  bestY = y;
  bestZ = z;
  bestV = function->value(bestX, bestY, bestZ);
}

SimpleMinimization::~SimpleMinimization() {}

void SimpleMinimization::find(double dr_ini, double dr_fin,
                              int idleStepsLimit) {

  std::cout << "Start " << std::endl;

#pragma omp parallel
  {
    drand48_data buff;
    srand48_r(time(NULL) + omp_get_thread_num(), &buff);

    double v, xnew, ynew, znew, vnew, dr, x_loc, y_loc, z_loc, v_loc;

    while (hasTimeToContinue()) {

      int idleSteps = 0; // liczba krokow, ktore nie poprawily lokalizacji
                         // inicjujemy losowo polozenie startowe w obrebie
                         // kwadratu o bokach od min do max
      dr = dr_ini;

      generateRandomPosition(x_loc, y_loc, z_loc, buff);
      v_loc = function->value(x_loc, y_loc,
                              z_loc); // wartosc funkcji w punkcie startowym

      while ((dr > dr_fin) && (idleSteps < idleStepsLimit)) {

        double xShift, yShift, zShift;
        drand48_r(&buff, &xShift);
        drand48_r(&buff, &yShift);
        drand48_r(&buff, &zShift);

        xnew = x_loc + (xShift - 0.5) * dr;
        ynew = y_loc + (yShift - 0.5) * dr;
        znew = z_loc + (zShift - 0.5) * dr;

        // upewniamy sie, ze nie opuscilismy przestrzeni poszukiwania
        // rozwiazania
        xnew = limitX(xnew);
        ynew = limitY(ynew);
        znew = limitZ(znew);

        // wartosc funkcji w nowym polozeniu
        vnew = function->value(xnew, ynew, znew);

        if (vnew < v_loc) {
          x_loc = xnew; // przenosimy sie do nowej, lepszej lokalizacji
          y_loc = ynew;
          z_loc = znew;
          v_loc = vnew;
          idleSteps = 0; // resetujemy licznik krokow, bez poprawy polozenia
        } else {
          idleSteps++; // nic sie nie stalo

          if (idleSteps == idleStepsLimit) {
            dr *= DR_SHRINK; // zmniejszamy dr
            idleSteps = 0;
          }
        }
      } // dr wciaz za duze
#pragma omp critical
      {
        x = x_loc;
        y = y_loc;
        z = z_loc;

        addToHistory();

        if (v_loc < bestV) { // znalezlismy najlepsze polozenie globalnie
          bestV = v_loc;
          bestX = x_loc;
          bestY = y_loc;
          bestZ = z_loc;
          v = v_loc;

          std::cout << "New better position: " << x << ", " << y << ", " << z
                    << " value = " << v << std::endl;
        }
      }

    } // mamy czas na obliczenia
  }
}

void SimpleMinimization::generateRandomPosition(double &xl, double &yl,
                                                double &zl,
                                                drand48_data &buff) {
  double xShift, yShift, zShift;
  drand48_r(&buff, &xShift);
  drand48_r(&buff, &yShift);
  drand48_r(&buff, &zShift);

  xl = xShift * (maxX - minX) + minX;
  yl = yShift * (maxY - minY) + minY;
  zl = zShift * (maxZ - minZ) + minZ;
}