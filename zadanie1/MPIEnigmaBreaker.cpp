

#include "MPIEnigmaBreaker.h"

#include <cmath>
#include <mpi.h>
#include <numeric>

MPIEnigmaBreaker::MPIEnigmaBreaker(Enigma *enigma,
                                   MessageComparator *comparator)
    : EnigmaBreaker(enigma, comparator) {

  MAX_ROTORS_VALUE = enigma->getLargestRotorSetting();
  NUMBER_OF_ROTORS_VALUES = MAX_ROTORS_VALUE + 1;
  COMBINATIONS_NUMBER =
      static_cast<uint64_t>(std::pow(NUMBER_OF_ROTORS_VALUES, rotors));
  MPI_Comm_rank(MPI_COMM_WORLD, &processRank);
  MPI_Comm_size(MPI_COMM_WORLD, &totalNumberOfProcesses);

  if (COMBINATIONS_NUMBER * 0.01 >= 100 * totalNumberOfProcesses) {
    operationsNumberForTest = 100;
  } else if (COMBINATIONS_NUMBER * 0.01 >= 10 * totalNumberOfProcesses) {
    operationsNumberForTest = 10;
  } else {
    operationsNumberForTest = 1;
  }

  if (COMBINATIONS_NUMBER <= 1000) {
    DIVISIONS_N = 1;
  } else {
    DIVISIONS_N = COMBINATIONS_NUMBER * 0.001;
  }

  while (((static_cast<double>(DIVISIONS_N) / (1024.0 * 1024.0)) *
          totalNumberOfProcesses * sizeof(uint64_t)) > 100) {
    DIVISIONS_N /= 10;
  }

  startPosition.resize(DIVISIONS_N);
  endPosition.resize(DIVISIONS_N);
}

MPIEnigmaBreaker::~MPIEnigmaBreaker() { delete[] rotorPositions; }

void MPIEnigmaBreaker::crackMessage() {

  double startedWaiting;
  bool foundSolution = false;
  std::array<uint, MAX_ROTORS> r{};

  if (processRank != MPI_ROOT_PROCESS_RANK) {
    RecvMessage();
    RecvExpectedMsg();
  }

  MPI_Barrier(MPI_COMM_WORLD);

  MeasureTime();

  MPI_Barrier(MPI_COMM_WORLD);

  if (foundGlobalSolution) {
    return;
  }
  startedWaiting = MPI_Wtime();

  for (int j = 0; j < DIVISIONS_N; j++) {
    for (uint i = startPosition[j]; i <= endPosition[j]; i++) {

      r = ConvertPositionToRotors(i);
      foundSolution = solutionFound(r.data());

      if ((MPI_Wtime() - startedWaiting) >= MAX_DELAY * 0.95) {

        MPI_Allreduce(&foundSolution, &foundGlobalSolution, 1, MPI_CXX_BOOL,
                      MPI_LOR, MPI_COMM_WORLD);

        startedWaiting = MPI_Wtime();
      }

      if (foundGlobalSolution || foundSolution) {

        break;
      }
    }

    if (foundGlobalSolution || foundSolution) {
      break;
    }
  }

  while (!foundGlobalSolution) {

    MPI_Allreduce(&foundSolution, &foundGlobalSolution, 1, MPI_CXX_BOOL,
                  MPI_LOR, MPI_COMM_WORLD);
  }

  CheckForSolution(foundSolution, r);

  MPI_Barrier(MPI_COMM_WORLD);
}

bool MPIEnigmaBreaker::solutionFound(uint *rotorSettingsProposal) {

  // copying rotorSettingsProposal to rotorPositions
  for (uint rotor = 0; rotor < rotors; rotor++) {
    rotorPositions[rotor] = rotorSettingsProposal[rotor];
  }

  // Setting rottors position
  enigma->setRotorPositions(rotorPositions);
  uint *decodedMessage = new uint[messageLength];

  for (uint messagePosition = 0; messagePosition < messageLength;
       messagePosition++) {
    decodedMessage[messagePosition] =
        enigma->code(messageToDecode[messagePosition]);
  }

  bool result = comparator->messageDecoded(decodedMessage);

  delete[] decodedMessage;

  return result;
}

void MPIEnigmaBreaker::getResult(uint *rotorPositions) {
  for (uint rotor = 0; rotor < rotors; rotor++) {
    rotorPositions[rotor] = this->rotorPositions[rotor];
  }
}

void MPIEnigmaBreaker::setMessageToDecode(uint *message, uint messageLength) {

  comparator->setMessageLength(messageLength);
  this->messageLength = messageLength;

  MPI_Bcast(&messageLength, 1, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK,
            MPI_COMM_WORLD);

  this->messageToDecode = message;
  messageProposal = new uint[messageLength];

  MPI_Bcast(message, messageLength, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK,
            MPI_COMM_WORLD);
}

void MPIEnigmaBreaker::setSampleToFind(uint *expected, uint expectedLength) {

  comparator->setExpectedFragment(expected, expectedLength);

  MPI_Bcast(&expectedLength, 1, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK,
            MPI_COMM_WORLD);

  MPI_Bcast(expected, expectedLength, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK,
            MPI_COMM_WORLD);
}

void MPIEnigmaBreaker::RecvMessage() {

  uint *msgSize = new uint;
  MPI_Bcast(msgSize, 1, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);
  comparator->setMessageLength(*msgSize);
  this->messageLength = *msgSize;

  uint *msg = new uint[*msgSize];

  MPI_Bcast(msg, *msgSize, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);
  this->messageToDecode = msg;
  messageProposal = new uint[messageLength];

  delete msgSize;
}

void MPIEnigmaBreaker::RecvExpectedMsg() {
  uint *msgSize = new uint;
  MPI_Bcast(msgSize, 1, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  uint *msg = new uint[*msgSize];
  MPI_Bcast(msg, *msgSize, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  comparator->setExpectedFragment(msg, *msgSize);

  delete msgSize;
}

double MPIEnigmaBreaker::CalculateSingleOperation() {

  double start = MPI_Wtime();

  bool foundSolution = false;
  std::array<uint, MAX_ROTORS> r{};

  for (int i = processRank * operationsNumberForTest;
       i < ((processRank + 1) * (operationsNumberForTest)); i++) {
    r = ConvertPositionToRotors(i);

    if (solutionFound(r.data())) {
      foundSolution = true;
      break;
    }
  }

  double end = MPI_Wtime();

  MPI_Allreduce(&foundSolution, &foundGlobalSolution, 1, MPI_CXX_BOOL, MPI_LOR,
                MPI_COMM_WORLD);

  CheckForSolution(foundSolution, r);

  double totalTimePerOperation =
      (end - start) / static_cast<double>(operationsNumberForTest);

  return (1.0 / totalTimePerOperation);
}

void MPIEnigmaBreaker::MeasureTime() {

  double *receivedResult;
  double *bufferSend;

  if (processRank == MPI_ROOT_PROCESS_RANK) {
    receivedResult = new double[totalNumberOfProcesses];
  }

  bufferSend = new double;
  *bufferSend = CalculateSingleOperation();

  if (foundGlobalSolution) {
    return;
  }

  MPI_Barrier(MPI_COMM_WORLD);
  MPI_Gather(bufferSend, 1, MPI_DOUBLE, receivedResult, 1, MPI_DOUBLE,
             MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  if (processRank == MPI_ROOT_PROCESS_RANK) {
    AssignWorkForProcesses(receivedResult);
  } else {
    RecvWorkFromRoot();
  }

  delete bufferSend;
  if (processRank == MPI_ROOT_PROCESS_RANK) {
    delete[] receivedResult;
  }
}

void MPIEnigmaBreaker::AssignWorkForProcesses(const double *times) {
  std::vector<double> measuredTimes(times, times + totalNumberOfProcesses);
  std::vector<uint64_t> beginPos(totalNumberOfProcesses * DIVISIONS_N);
  std::vector<uint64_t> endPos(totalNumberOfProcesses * DIVISIONS_N);

  const auto accumulatedTime =
      (1.0 / std::accumulate(measuredTimes.begin(), measuredTimes.end(), 0.0));

  uint64_t processCtr = totalNumberOfProcesses * operationsNumberForTest;

  for (int j = 0; j < DIVISIONS_N; j++) {

    for (int i = 0; i < totalNumberOfProcesses - 1; i++) {
      uint64_t operationsPerProcess = (COMBINATIONS_NUMBER / DIVISIONS_N) *
                                      (measuredTimes[i] * accumulatedTime);

      if (operationsPerProcess < 1) {
        operationsPerProcess = 1;
      }

      beginPos[i + (j * totalNumberOfProcesses)] = processCtr;
      endPos[i + (j * totalNumberOfProcesses)] =
          processCtr + operationsPerProcess - 1;
      processCtr += operationsPerProcess;
    }

    beginPos[((j + 1) * totalNumberOfProcesses) - 1] = processCtr;

    if (j != (DIVISIONS_N - 1)) {
      endPos[((j + 1) * totalNumberOfProcesses) - 1] =
          ((j + 1) * (COMBINATIONS_NUMBER / DIVISIONS_N)) - 1;
    } else {
      endPos[((j + 1) * totalNumberOfProcesses) - 1] = COMBINATIONS_NUMBER - 1;
    }

    processCtr = endPos[((j + 1) * totalNumberOfProcesses) - 1];
  }

  auto start = MPI_Wtime();
  // Send begin pos
  MPI_Bcast(beginPos.data(), totalNumberOfProcesses * DIVISIONS_N, MPI_UINT64_T,
            MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  MPI_Bcast(endPos.data(), totalNumberOfProcesses * DIVISIONS_N, MPI_UINT64_T,
            MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  for (int j = 0; j < DIVISIONS_N; j++) {
    startPosition[j] = beginPos[processRank + (j * totalNumberOfProcesses)];
    endPosition[j] = endPos[processRank + (j * totalNumberOfProcesses)];
  }

  auto end = MPI_Wtime();
}

void MPIEnigmaBreaker::RecvWorkFromRoot() {
  std::vector<uint64_t> beginPos(totalNumberOfProcesses * DIVISIONS_N);
  std::vector<uint64_t> endPos(totalNumberOfProcesses * DIVISIONS_N);

  MPI_Bcast(beginPos.data(), totalNumberOfProcesses * DIVISIONS_N, MPI_UINT64_T,
            MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  for (int j = 0; j < DIVISIONS_N; j++) {
    startPosition[j] = beginPos[processRank + (j * totalNumberOfProcesses)];
  }

  MPI_Bcast(endPos.data(), totalNumberOfProcesses * DIVISIONS_N, MPI_UINT64_T,
            MPI_ROOT_PROCESS_RANK, MPI_COMM_WORLD);

  for (int j = 0; j < DIVISIONS_N; j++) {

    endPosition[j] = endPos[processRank + (j * totalNumberOfProcesses)];
  }
}

std::array<uint, MAX_ROTORS>
MPIEnigmaBreaker::ConvertPositionToRotors(uint64_t number) {

  std::array<uint, MAX_ROTORS> rotorsPosition{};
  if (number == 0) {
    return rotorsPosition;
  }

  for (int i = rotors - 1; i >= 0; i--) {

    rotorsPosition[i] = (number % NUMBER_OF_ROTORS_VALUES);
    number /= (NUMBER_OF_ROTORS_VALUES);
  }

  return rotorsPosition;
}

void MPIEnigmaBreaker::CheckForSolution(bool currentProcessStatus,
                                        std::array<uint, MAX_ROTORS> &r) {
  std::array<uint, MAX_ROTORS> solution;

  if (currentProcessStatus && processRank != MPI_ROOT_PROCESS_RANK) {
    MPI_Send(r.data(), MAX_ROTORS, MPI_UNSIGNED, MPI_ROOT_PROCESS_RANK, 0,
             MPI_COMM_WORLD);
  }

  if (processRank == MPI_ROOT_PROCESS_RANK && !currentProcessStatus &&
      foundGlobalSolution) {

    MPI_Recv(solution.data(), MAX_ROTORS, MPI_UNSIGNED, MPI_ANY_SOURCE, 0,
             MPI_COMM_WORLD, MPI_STATUS_IGNORE);

    for (int i = 0; i < rotors; i++) {
      this->rotorPositions[i] = solution[i];
    }
  }
}
