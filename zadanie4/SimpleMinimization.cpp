#include "SimpleMinimization.h"

#include <math.h>
#include <omp.h>
#include <stdlib.h>
#include <sys/time.h>

#include <chrono>
#include <iostream>
#include <thread>
const double DR_SHRINK = 0.8;

using namespace std;
using namespace std::chrono_literals;

SimpleMinimization::SimpleMinimization(Function *f, double timeLimit) : Minimization(f, timeLimit)
{
    for (int i = 0; i < 24; i++)
    {
        auto struct_ptr = std::make_unique<drand48_data>();
        srand48_r(time(NULL) + i * 100, struct_ptr.get());
        randBufferV.push_back(std::move(struct_ptr));
    }

    generateRandomPosition();
    bestX = x;
    bestY = y;
    bestZ = z;

    bestV = function->value(bestX, bestY, bestZ);
}

SimpleMinimization::~SimpleMinimization() {}

void SimpleMinimization::find(double dr_ini, double dr_fin, int idleStepsLimit)
{
    double v, xnew, ynew, znew, vnew, dr;
    double xl, yl, zl;
    int idleSteps = 0;  // liczba krokow, ktore nie poprawily lokalizacji

    std::cout << "Start " << std::endl;

    while (hasTimeToContinue())
    {
        // inicjujemy losowo polozenie startowe w obrebie kwadratu o bokach od min do max

        generateRandomPosition();
        v = function->value(x, y, z);

#pragma omp parallel private(xnew, ynew, znew, dr, idleSteps, vnew)
        {
            idleSteps = 0;
            dr = dr_ini;

            while ((dr > dr_fin) && (idleSteps < idleStepsLimit))
            {
                double xShift, yShift, zShift;

                drand48_r(randBufferV[omp_get_thread_num()].get(), &xShift);
                drand48_r(randBufferV[omp_get_thread_num()].get(), &yShift);
                drand48_r(randBufferV[omp_get_thread_num()].get(), &zShift);

                xnew = x + (xShift - 0.5) * dr;
                ynew = y + (yShift - 0.5) * dr;
                znew = z + (zShift - 0.5) * dr;

                // upewniamy sie, ze nie opuscilismy przestrzeni poszukiwania rozwiazania
                xnew = limitX(xnew);
                ynew = limitY(ynew);
                znew = limitZ(znew);

                // wartosc funkcji w nowym polozeniu
                vnew = function->value(xnew, ynew, znew);

                if (vnew < v)
                {
#pragma omp critical
                    {
                        x = xnew;  // przenosimy sie do nowej, lepszej lokalizacji
                        y = ynew;
                        z = znew;
                        v = vnew;
                    }

                    idleSteps = 0;  // resetujemy licznik krokow, bez poprawy polozenia
                }
                else
                {
                    idleSteps++;  // nic sie nie stalo

                    if (idleSteps > idleStepsLimit)
                    {
                        dr *= DR_SHRINK;  // zmniejszamy dr
                        idleSteps = 0;
                    }
                }

            }  // dr wciaz za duze

#pragma omp critical
            {
                addToHistory();

                if (v < bestV)
                {  // znalezlismy najlepsze polozenie globalnie
                    bestV = v;
                    bestX = x;
                    bestY = y;
                    bestZ = z;
                    std::cout << "New better position: " << x << ", " << y << ", " << z << " value = " << v << std::endl;
                }
            }
        }

    }  // mamy czas na obliczenia
}

void SimpleMinimization::generateRandomPosition()
{
    double xShift, yShift, zShift;
    drand48_r(randBufferV[omp_get_thread_num()].get(), &xShift);
    drand48_r(randBufferV[omp_get_thread_num()].get(), &yShift);
    drand48_r(randBufferV[omp_get_thread_num()].get(), &zShift);

    // std::cout << "Thread " << omp_get_thread_num() << " xshift " << xShift << std::endl;

    x = xShift * (maxX - minX) + minX;
    y = yShift * (maxY - minY) + minY;
    z = zShift * (maxZ - minZ) + minZ;
}
